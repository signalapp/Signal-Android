package org.thoughtcrime.securesms.ringrtc;


import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import org.signal.ringrtc.CallConnection;
import org.signal.ringrtc.CallConnectionFactory;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.SignalMessageRecipient;

import org.thoughtcrime.securesms.logging.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import static org.thoughtcrime.securesms.ringrtc.CameraState.Direction.BACK;
import static org.thoughtcrime.securesms.ringrtc.CameraState.Direction.FRONT;
import static org.thoughtcrime.securesms.ringrtc.CameraState.Direction.NONE;
import static org.thoughtcrime.securesms.ringrtc.CameraState.Direction.PENDING;

public class CallConnectionWrapper {
  private static final String TAG = Log.tag(CallConnectionWrapper.class);

  @NonNull  private final CallConnection callConnection;
  @NonNull  private final AudioTrack     audioTrack;
  @NonNull  private final AudioSource    audioSource;
  @NonNull  private final Camera         camera;
  @Nullable private final VideoSource    videoSource;
  @Nullable private final VideoTrack     videoTrack;

  public CallConnectionWrapper(@NonNull Context                     context,
                               @NonNull CallConnectionFactory       factory,
                               @NonNull CallConnection.Observer     observer,
                               @NonNull VideoSink                   localRenderer,
                               @NonNull CameraEventListener         cameraEventListener,
                               @NonNull EglBase                     eglBase,
                               boolean                              hideIp,
                               long                                 callId,
                               boolean                              outBound,
                               @NonNull SignalMessageRecipient      recipient,
                               @NonNull SignalServiceAccountManager accountManager)
    throws UnregisteredUserException, IOException, CallException
  {

    CallConnection.Configuration configuration = new CallConnection.Configuration(callId,
                                                                                  outBound,
                                                                                  recipient,
                                                                                  accountManager,
                                                                                  hideIp);

    this.callConnection = factory.createCallConnection(configuration, observer);
    this.callConnection.setAudioPlayout(false);
    this.callConnection.setAudioRecording(false);

    MediaStream      mediaStream      = factory.createLocalMediaStream("ARDAMS");
    MediaConstraints audioConstraints = new MediaConstraints();

    audioConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    this.audioSource = factory.createAudioSource(audioConstraints);
    this.audioTrack  = factory.createAudioTrack("ARDAMSa0", audioSource);
    this.audioTrack.setEnabled(false);
    mediaStream.addTrack(audioTrack);

    this.camera = new Camera(context, cameraEventListener);

    if (camera.capturer != null) {
      this.videoSource = factory.createVideoSource(false);
      this.videoTrack  = factory.createVideoTrack("ARDAMSv0", videoSource);

      camera.capturer.initialize(SurfaceTextureHelper.create("WebRTC-SurfaceTextureHelper", eglBase.getEglBaseContext()), context, videoSource.getCapturerObserver());

      this.videoTrack.addSink(localRenderer);
      this.videoTrack.setEnabled(false);
      mediaStream.addTrack(videoTrack);
    } else {
      this.videoSource = null;
      this.videoTrack  = null;
    }

    this.callConnection.addStream(mediaStream);
  }

  public boolean addIceCandidate(IceCandidate candidate) {
    return callConnection.addIceCandidate(candidate);
  }

  public void sendOffer() throws CallException {
    callConnection.sendOffer();
  }

  public boolean validateResponse(SignalMessageRecipient recipient, Long inCallId)
    throws CallException
  {
    return callConnection.validateResponse(recipient, inCallId);
  }

  public void handleOfferAnswer(String sessionDescription) throws CallException {
    callConnection.handleOfferAnswer(sessionDescription);
  }

  public void acceptOffer(String offer) throws CallException {
    callConnection.acceptOffer(offer);
  }

  public void hangUp() throws CallException {
    callConnection.hangUp();
  }

  public void answerCall() throws CallException {
    callConnection.answerCall();
  }

  public void setVideoEnabled(boolean enabled) throws CallException {
    if (videoTrack != null) {
      videoTrack.setEnabled(enabled);
    }
    camera.setEnabled(enabled);
    callConnection.sendVideoStatus(enabled);
  }

  public void flipCamera() {
    camera.flip();
  }

  public CameraState getCameraState() {
    return new CameraState(camera.getActiveDirection(), camera.getCount());
  }

  public void setCommunicationMode() {
    callConnection.setAudioPlayout(true);
    callConnection.setAudioRecording(true);
  }

  public void setAudioEnabled(boolean enabled) {
    audioTrack.setEnabled(enabled);
  }

  public void dispose() {
    camera.dispose();

    if (videoSource != null) {
      videoSource.dispose();
    }

    audioSource.dispose();
    callConnection.dispose();
  }

  private static class Camera implements CameraVideoCapturer.CameraSwitchHandler {

    @Nullable
    private final CameraVideoCapturer   capturer;
    private final CameraEventListener   cameraEventListener;
    private final int                   cameraCount;

    private CameraState.Direction activeDirection;
    private boolean               enabled;

    Camera(@NonNull Context context, @NonNull CameraEventListener cameraEventListener)
    {
      this.cameraEventListener    = cameraEventListener;
      CameraEnumerator enumerator = getCameraEnumerator(context);
      cameraCount                 = enumerator.getDeviceNames().length;

      CameraVideoCapturer capturerCandidate = createVideoCapturer(enumerator, FRONT);
      if (capturerCandidate != null) {
        activeDirection = FRONT;
      } else {
        capturerCandidate = createVideoCapturer(enumerator, BACK);
        if (capturerCandidate != null) {
          activeDirection = BACK;
        } else {
          activeDirection = NONE;
        }
      }
      capturer = capturerCandidate;
    }

    void flip() {
      if (capturer == null || cameraCount < 2) {
        throw new AssertionError("Tried to flip the camera, but we only have " + cameraCount +
                                 " of them.");
      }
      activeDirection = PENDING;
      capturer.switchCamera(this);
    }

    void setEnabled(boolean enabled) {
      this.enabled = enabled;

      if (capturer == null) {
        return;
      }

      try {
        if (enabled) {
          capturer.startCapture(1280, 720, 30);
        } else {
          capturer.stopCapture();
        }
      } catch (InterruptedException e) {
        Log.w(TAG, "Got interrupted while trying to stop video capture", e);
      }
    }

    void dispose() {
      if (capturer != null) {
        capturer.dispose();
      }
    }

    int getCount() {
      return cameraCount;
    }

    @NonNull CameraState.Direction getActiveDirection() {
      return enabled ? activeDirection : NONE;
    }

    @Nullable CameraVideoCapturer getCapturer() {
      return capturer;
    }

    private @Nullable CameraVideoCapturer createVideoCapturer(@NonNull CameraEnumerator enumerator,
                                                              @NonNull CameraState.Direction direction)
    {
      String[] deviceNames = enumerator.getDeviceNames();
      for (String deviceName : deviceNames) {
        if ((direction == FRONT && enumerator.isFrontFacing(deviceName)) ||
            (direction == BACK  && enumerator.isBackFacing(deviceName)))
        {
          return enumerator.createCapturer(deviceName, null);
        }
      }

      return null;
    }

    private @NonNull CameraEnumerator getCameraEnumerator(@NonNull Context context) {
      boolean camera2EnumeratorIsSupported = false;
      try {
        camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(context);
      } catch (final Throwable throwable) {
        Log.w(TAG, "Camera2Enumator.isSupport() threw.", throwable);
      }

      Log.i(TAG, "Camera2 enumerator supported: " + camera2EnumeratorIsSupported);

      return camera2EnumeratorIsSupported ? new Camera2Enumerator(context)
                                          : new Camera1Enumerator(true);
    }

    @Override
    public void onCameraSwitchDone(boolean isFrontFacing) {
      activeDirection = isFrontFacing ? FRONT : BACK;
      cameraEventListener.onCameraSwitchCompleted(new CameraState(getActiveDirection(), getCount()));
    }

    @Override
    public void onCameraSwitchError(String errorMessage) {
      Log.e(TAG, "onCameraSwitchError: " + errorMessage);
      cameraEventListener.onCameraSwitchCompleted(new CameraState(getActiveDirection(), getCount()));
    }
  }

  public interface CameraEventListener {
    void onCameraSwitchCompleted(@NonNull CameraState newCameraState);
  }
}
