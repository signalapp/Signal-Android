package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.ringrtc.CallId;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.WebRtcActionProcessor;

import java.util.HashMap;
import java.util.Map;

/**
 * Represent the entire state of the call system.
 */
public final class WebRtcServiceState {

  WebRtcActionProcessor       actionProcessor;
  Map<CallId, CallSetupState> callSetupStates;
  CallInfoState               callInfoState;
  LocalDeviceState            localDeviceState;
  VideoState                  videoState;

  public WebRtcServiceState(@NonNull WebRtcActionProcessor actionProcessor) {
    this.actionProcessor  = actionProcessor;
    this.callSetupStates  = new HashMap<>();
    this.callInfoState    = new CallInfoState();
    this.localDeviceState = new LocalDeviceState();
    this.videoState       = new VideoState();
  }

  public WebRtcServiceState(@NonNull WebRtcServiceState toCopy) {
    this.actionProcessor  = toCopy.actionProcessor;
    this.callInfoState    = toCopy.callInfoState.duplicate();
    this.localDeviceState = toCopy.localDeviceState.duplicate();
    this.videoState       = new VideoState(toCopy.videoState);
    this.callSetupStates  = new HashMap<>();

    for (Map.Entry<CallId, CallSetupState> entry : toCopy.callSetupStates.entrySet()) {
      this.callSetupStates.put(entry.getKey(), entry.getValue().duplicate());
    }
  }

  public @NonNull WebRtcActionProcessor getActionProcessor() {
    return actionProcessor;
  }


  public @NonNull CallSetupState getCallSetupState(@NonNull RemotePeer remotePeer) {
    return getCallSetupState(remotePeer.getCallId());
  }

  public @NonNull CallSetupState getCallSetupState(@Nullable CallId callId) {
    if (callId == null) {
      return new CallSetupState();
    }

    if (!callSetupStates.containsKey(callId)) {
      callSetupStates.put(callId, new CallSetupState());
    }

    //noinspection ConstantConditions
    return callSetupStates.get(callId);
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
