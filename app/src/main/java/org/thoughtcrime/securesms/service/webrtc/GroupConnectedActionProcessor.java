package org.thoughtcrime.securesms.service.webrtc;

import androidx.annotation.NonNull;

import org.signal.ringrtc.CallException;
import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;

/**
 * Process actions for when the call has at least once been connected and joined.
 */
public class GroupConnectedActionProcessor extends GroupActionProcessor {

  private static final String TAG = Log.tag(GroupConnectedActionProcessor.class);

  public GroupConnectedActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(TAG, "handleSetEnableVideo():");

    GroupCall groupCall = currentState.getCallInfoState().requireGroupCall();
    Camera    camera    = currentState.getVideoState().requireCamera();

    try {
      groupCall.setOutgoingVideoMuted(!enable);
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable set video muted", e);
    }
    camera.setEnabled(enable);

    currentState = currentState.builder()
                               .changeLocalDeviceState()
                               .cameraState(camera.getCameraState())
                               .build();

    WebRtcUtil.enableSpeakerPhoneIfNeeded(webRtcInteractor.getWebRtcCallService(), currentState.getCallSetupState().isEnableVideoOnCreate());

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetMuteAudio(@NonNull WebRtcServiceState currentState, boolean muted) {
    try {
      currentState.getCallInfoState().requireGroupCall().setOutgoingAudioMuted(muted);
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to set audio muted", e);
    }

    return currentState.builder()
                       .changeLocalDeviceState()
                       .isMicrophoneEnabled(!muted)
                       .build();
  }

  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    Log.i(TAG, "handleLocalHangup():");

    GroupCall groupCall = currentState.getCallInfoState().requireGroupCall();
    try {
      groupCall.disconnect();
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to disconnect from group call", e);
    }

    currentState = currentState.builder()
                               .changeCallInfoState()
                               .callState(WebRtcViewModel.State.CALL_DISCONNECTED)
                               .groupCallState(WebRtcViewModel.GroupCallState.DISCONNECTED)
                               .build();

    webRtcInteractor.sendMessage(currentState);

    return terminateGroupCall(currentState);
  }
}
