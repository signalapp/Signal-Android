package org.thoughtcrime.securesms.webrtc;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PeerConnectionWrapper {
  private static final String TAG = PeerConnectionWrapper.class.getSimpleName();

  private static final PeerConnection.IceServer STUN_SERVER = new PeerConnection.IceServer("stun:stun1.l.google.com:19302");

  @NonNull  private final PeerConnection peerConnection;
  @NonNull  private final AudioTrack     audioTrack;
  @NonNull  private final AudioSource    audioSource;

  @Nullable private final VideoCapturer  videoCapturer;
  @Nullable private final VideoSource    videoSource;
  @Nullable private final VideoTrack     videoTrack;

  public PeerConnectionWrapper(@NonNull Context context,
                               @NonNull PeerConnectionFactory factory,
                               @NonNull PeerConnection.Observer observer,
                               @NonNull VideoRenderer.Callbacks localRenderer,
                               @NonNull List<PeerConnection.IceServer> turnServers,
                               boolean hideIp)
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

    this.videoCapturer  = createVideoCapturer(context);

    MediaStream mediaStream = factory.createLocalMediaStream("ARDAMS");
    this.audioSource = factory.createAudioSource(audioConstraints);
    this.audioTrack  = factory.createAudioTrack("ARDAMSa0", audioSource);
    this.audioTrack.setEnabled(false);
    mediaStream.addTrack(audioTrack);

    if (videoCapturer != null) {
      this.videoSource = factory.createVideoSource(videoCapturer);
      this.videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);

      this.videoTrack.addRenderer(new VideoRenderer(localRenderer));
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

    if (this.videoCapturer != null) {
      try {
        if (enabled) this.videoCapturer.startCapture(1280, 720, 30);
        else         this.videoCapturer.stopCapture();
      } catch (InterruptedException e) {
        Log.w(TAG, e);
      }
    }
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
    if (this.videoCapturer != null) {
      try {
        this.videoCapturer.stopCapture();
      } catch (InterruptedException e) {
        Log.w(TAG, e);
      }
      this.videoCapturer.dispose();
    }

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

  private @Nullable CameraVideoCapturer createVideoCapturer(@NonNull Context context) {
    boolean camera2EnumeratorIsSupported = false;
    try {
      camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(context);
    } catch (final Throwable throwable) {
      Log.w(TAG, "Camera2Enumator.isSupport() threw.", throwable);
    }

    Log.w(TAG, "Camera2 enumerator supported: " + camera2EnumeratorIsSupported);
    CameraEnumerator enumerator;

    if (camera2EnumeratorIsSupported) enumerator = new Camera2Enumerator(context);
    else                              enumerator = new Camera1Enumerator(true);

    String[] deviceNames = enumerator.getDeviceNames();

    for (String deviceName : deviceNames) {
      if (enumerator.isFrontFacing(deviceName)) {
        Log.w(TAG, "Creating front facing camera capturer.");
        final CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          Log.w(TAG, "Found front facing capturer: " + deviceName);

          return videoCapturer;
        }
      }
    }

    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        Log.w(TAG, "Creating other camera capturer.");
        final CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          Log.w(TAG, "Found other facing capturer: " + deviceName);
          return videoCapturer;
        }
      }
    }

    Log.w(TAG, "Video capture not supported!");
    return null;
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
}
