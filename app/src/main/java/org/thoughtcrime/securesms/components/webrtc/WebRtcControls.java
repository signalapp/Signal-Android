package org.thoughtcrime.securesms.components.webrtc;

import androidx.annotation.NonNull;

public final class WebRtcControls {

  public static final WebRtcControls NONE = new WebRtcControls();
  public static final WebRtcControls PIP  = new WebRtcControls(false, false, false, false, true, CallState.NONE, WebRtcAudioOutput.HANDSET);

  private final boolean           isRemoteVideoEnabled;
  private final boolean           isLocalVideoEnabled;
  private final boolean           isMoreThanOneCameraAvailable;
  private final boolean           isBluetoothAvailable;
  private final boolean           isInPipMode;
  private final CallState         callState;
  private final WebRtcAudioOutput audioOutput;

  private WebRtcControls() {
    this(false, false, false, false, false, CallState.NONE, WebRtcAudioOutput.HANDSET);
  }

  WebRtcControls(boolean isLocalVideoEnabled,
                 boolean isRemoteVideoEnabled,
                 boolean isMoreThanOneCameraAvailable,
                 boolean isBluetoothAvailable,
                 boolean isInPipMode,
                 @NonNull CallState callState,
                 @NonNull WebRtcAudioOutput audioOutput)
  {
    this.isLocalVideoEnabled          = isLocalVideoEnabled;
    this.isRemoteVideoEnabled         = isRemoteVideoEnabled;
    this.isBluetoothAvailable         = isBluetoothAvailable;
    this.isMoreThanOneCameraAvailable = isMoreThanOneCameraAvailable;
    this.isInPipMode                  = isInPipMode;
    this.callState                    = callState;
    this.audioOutput                  = audioOutput;
  }

  boolean displayEndCall() {
    return isOngoing();
  }

  boolean displayMuteAudio() {
    return isOngoing();
  }

  boolean displayVideoToggle() {
    return isOngoing();
  }

  boolean displayAudioToggle() {
    return isOngoing() && (!isLocalVideoEnabled || isBluetoothAvailable);
  }

  boolean displayCameraToggle() {
    return isOngoing() && isLocalVideoEnabled && isMoreThanOneCameraAvailable;
  }

  boolean displayAnswerWithAudio() {
    return isIncoming() && isRemoteVideoEnabled;
  }

  boolean displayIncomingCallButtons() {
    return isIncoming();
  }

  boolean enableHandsetInAudioToggle() {
    return !isLocalVideoEnabled;
  }

  boolean enableHeadsetInAudioToggle() {
    return isBluetoothAvailable;
  }

  boolean isFadeOutEnabled() {
    return isOngoing() && isRemoteVideoEnabled;
  }

  boolean displaySmallOngoingCallButtons() {
    return isOngoing() && displayAudioToggle() && displayCameraToggle();
  }

  boolean displayLargeOngoingCallButtons() {
    return isOngoing() && !(displayAudioToggle() && displayCameraToggle());
  }

  boolean displayTopViews() {
    return !isInPipMode;
  }

  WebRtcAudioOutput getAudioOutput() {
    return audioOutput;
  }

  private boolean isOngoing() {
    return callState == CallState.ONGOING;
  }

  private boolean isIncoming() {
    return callState == CallState.INCOMING;
  }

  public enum CallState {
    NONE,
    INCOMING,
    ONGOING
  }
}
