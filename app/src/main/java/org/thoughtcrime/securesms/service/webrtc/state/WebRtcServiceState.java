package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.service.webrtc.WebRtcActionProcessor;

/**
 * Represent the entire state of the call system.
 */
public final class WebRtcServiceState {

  WebRtcActionProcessor actionProcessor;
  CallSetupState        callSetupState;
  CallInfoState         callInfoState;
  LocalDeviceState      localDeviceState;
  VideoState            videoState;

  public WebRtcServiceState(@NonNull WebRtcActionProcessor actionProcessor) {
    this.actionProcessor  = actionProcessor;
    this.callSetupState   = new CallSetupState();
    this.callInfoState    = new CallInfoState();
    this.localDeviceState = new LocalDeviceState();
    this.videoState       = new VideoState();
  }

  public WebRtcServiceState(@NonNull WebRtcServiceState toCopy) {
    this.actionProcessor  = toCopy.actionProcessor;
    this.callSetupState   = new CallSetupState(toCopy.callSetupState);
    this.callInfoState    = new CallInfoState(toCopy.callInfoState);
    this.localDeviceState = new LocalDeviceState(toCopy.localDeviceState);
    this.videoState       = new VideoState(toCopy.videoState);
  }

  public @NonNull WebRtcActionProcessor getActionProcessor() {
    return actionProcessor;
  }

  public @NonNull CallSetupState getCallSetupState() {
    return callSetupState;
  }

  public @NonNull CallInfoState getCallInfoState() {
    return callInfoState;
  }

  public @NonNull LocalDeviceState getLocalDeviceState() {
    return localDeviceState;
  }

  public @NonNull VideoState getVideoState() {
    return videoState;
  }

  public @NonNull WebRtcServiceStateBuilder builder() {
    return new WebRtcServiceStateBuilder(this);
  }
}
