package org.thoughtcrime.securesms.webrtc;


import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.thoughtcrime.securesms.webrtc.CameraState.Direction.BACK;
import static org.thoughtcrime.securesms.webrtc.CameraState.Direction.FRONT;
import static org.thoughtcrime.securesms.webrtc.CameraState.Direction.NONE;
import static org.thoughtcrime.securesms.webrtc.CameraState.Direction.PENDING;

public class PeerConnectionWrapper {
  private static final String TAG = PeerConnectionWrapper.class.getSimpleName();

  private static final PeerConnection.IceServer STUN_SERVER = new PeerConnection.IceServer("stun:stun1.l.google.com:19302");

  @NonNull  private final PeerConnection peerConnection;
  @NonNull  private final AudioTrack     audioTrack;
  @NonNull  private final AudioSource    audioSource;
  @NonNull  private final Camera         camera;
  @Nullable private final VideoSource    videoSource;
  @Nullable private final VideoTrack     videoTrack;

  public PeerConnectionWrapper(@NonNull Context                        context,
                               @NonNull PeerConnectionFactory          factory,
                               @NonNull PeerConnection.Observer        observer,
                               @NonNull VideoSink                      localRenderer,
                               @NonNull List<PeerConnection.IceServer> turnServers,
                               @NonNull CameraEventListener            cameraEventListener,
                               @NonNull EglBase                        eglBase,
                               boolean                                 hideIp)
  {
    List<PeerConnection.IceServer> iceServers = new LinkedList<>();
    iceServers.add(STUN_SERVER);
    iceServers.addAll(turnServers);

    MediaConstraints                constraints      = new MediaConstraints();
    MediaConstraints                audioConstraints = new MediaConstraints();
    PeerConnection.RTCConfiguration configuration    = new PeerConnection.RTCConfiguration(iceServers);

    configuration.bundlePolicy  = PeerConnection.BundlePolicy.MAXBUNDLE;
    configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;

    if (hideIp) {
      configuration.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
    }

    constraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    audioConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

    this.peerConnection = factory.createPeerConnection(configuration, constraints, observer);
    this.peerConnection.setAudioPlayout(false);
    this.peerConnection.setAudioRecording(false);

    MediaStream mediaStream = factory.createLocalMediaStream("ARDAMS");
    this.audioSource = factory.createAudioSource(audioConstraints);
    this.audioTrack  = factory.createAudioTrack("ARDAMSa0", audioSource);
    this.audioTrack.setEnabled(false);
    mediaStream.addTrack(audioTrack);

    this.camera = new Camera(context, cameraEventListener);

    if (camera.capturer != null) {
      this.videoSource = factory.createVideoSource(false);
      this.videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);

      camera.capturer.initialize(SurfaceTextureHelper.create("WebRTC-SurfaceTextureHelper", eglBase.getEglBaseContext()), context, videoSource.getCapturerObserver());

      this.videoTrack.addSink(localRenderer);
      this.videoTrack.setEnabled(false);
      mediaStream.addTrack(videoTrack);
    } else {
      this.videoSource = null;
      this.videoTrack  = null;
    }

    this.peerConnection.addStream(mediaStream);
  }

  public void setVideoEnabled(boolean enabled) {
    if (this.videoTrack != null) {
      this.videoTrack.setEnabled(enabled);
    }
    camera.setEnabled(enabled);
  }

  public void flipCamera() {
    camera.flip();
  }

  public CameraState getCameraState() {
    return new CameraState(camera.getActiveDirection(), camera.getCount());
  }

  public void setCommunicationMode() {
    this.peerConnection.setAudioPlayout(true);
    this.peerConnection.setAudioRecording(true);
  }

  public void setAudioEnabled(boolean enabled) {
    this.audioTrack.setEnabled(enabled);
  }

  public DataChannel createDataChannel(String name) {
    DataChannel.Init dataChannelConfiguration = new DataChannel.Init();
    dataChannelConfiguration.ordered = true;

    return this.peerConnection.createDataChannel(name, dataChannelConfiguration);
  }

  public SessionDescription createOffer(MediaConstraints mediaConstraints) throws PeerConnectionException {
    final SettableFuture<SessionDescription> future = new SettableFuture<>();

    peerConnection.createOffer(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        future.set(sdp);
      }

      @Override
      public void onCreateFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }

      @Override
      public void onSetSuccess() {
        throw new AssertionError();
      }

      @Override
      public void onSetFailure(String error) {
        throw new AssertionError();
      }
    }, mediaConstraints);

    try {
      return correctSessionDescription(future.get());
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public SessionDescription createAnswer(MediaConstraints mediaConstraints) throws PeerConnectionException {
    final SettableFuture<SessionDescription> future = new SettableFuture<>();

    peerConnection.createAnswer(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        future.set(sdp);
      }

      @Override
      public void onCreateFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }

      @Override
      public void onSetSuccess() {
        throw new AssertionError();
      }

      @Override
      public void onSetFailure(String error) {
        throw new AssertionError();
      }
    }, mediaConstraints);

    try {
      return correctSessionDescription(future.get());
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public void setRemoteDescription(SessionDescription sdp) throws PeerConnectionException {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    peerConnection.setRemoteDescription(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {}

      @Override
      public void onCreateFailure(String error) {}

      @Override
      public void onSetSuccess() {
        future.set(true);
      }

      @Override
      public void onSetFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }
    }, sdp);

    try {
      future.get();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public void setLocalDescription(SessionDescription sdp) throws PeerConnectionException {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    peerConnection.setLocalDescription(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        throw new AssertionError();
      }

      @Override
      public void onCreateFailure(String error) {
        throw new AssertionError();
      }

      @Override
      public void onSetSuccess() {
        future.set(true);
      }

      @Override
      public void onSetFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }
    }, sdp);

    try {
      future.get();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public void dispose() {
    this.camera.dispose();

    if (this.videoSource != null) {
      this.videoSource.dispose();
    }

    this.audioSource.dispose();
    this.peerConnection.close();
    this.peerConnection.dispose();
  }

  public boolean addIceCandidate(IceCandidate candidate) {
    return this.peerConnection.addIceCandidate(candidate);
  }


  private SessionDescription correctSessionDescription(SessionDescription sessionDescription) {
    String updatedSdp = sessionDescription.description.replaceAll("(a=fmtp:111 ((?!cbr=).)*)\r?\n", "$1;cbr=1\r\n");
    updatedSdp = updatedSdp.replaceAll(".+urn:ietf:params:rtp-hdrext:ssrc-audio-level.*\r?\n", "");

    return new SessionDescription(sessionDescription.type, updatedSdp);
  }

  public static class PeerConnectionException extends Exception {
    public PeerConnectionException(String error) {
      super(error);
    }

    public PeerConnectionException(Throwable throwable) {
      super(throwable);
    }
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
      this.cameraEventListener = cameraEventListener;
      CameraEnumerator enumerator = getCameraEnumerator(context);
      cameraCount = enumerator.getDeviceNames().length;

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
        Log.w(TAG, "Tried to flip the camera, but we only have " + cameraCount + " of them.");
        return;
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
