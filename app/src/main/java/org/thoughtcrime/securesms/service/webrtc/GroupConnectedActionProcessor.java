package org.thoughtcrime.securesms.service.webrtc;

import androidx.annotation.NonNull;

import org.signal.ringrtc.CallException;
import org.signal.ringrtc.GroupCall;
import org.signal.ringrtc.PeekInfo;
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
  protected @NonNull WebRtcServiceState handleGroupLocalDeviceStateChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupLocalDeviceStateChanged():");

    GroupCall                  groupCall       = currentState.getCallInfoState().requireGroupCall();
    GroupCall.LocalDeviceState device          = groupCall.getLocalDeviceState();
    GroupCall.ConnectionState  connectionState = device.getConnectionState();
    GroupCall.JoinState        joinState       = device.getJoinState();

    Log.i(tag, "local device changed: " + connectionState + " " + joinState);

    WebRtcViewModel.GroupCallState groupCallState = WebRtcUtil.groupCallStateForConnection(connectionState);

    if (connectionState == GroupCall.ConnectionState.CONNECTED || connectionState == GroupCall.ConnectionState.CONNECTING) {
      if (joinState == GroupCall.JoinState.JOINED) {
        groupCallState = WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINED;
      } else if (joinState == GroupCall.JoinState.JOINING) {
        groupCallState = WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINING;
      }
    }

    return currentState.builder().changeCallInfoState()
                       .groupCallState(groupCallState)
                       .build();
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

  @Override
  protected @NonNull WebRtcServiceState handleGroupJoinedMembershipChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupJoinedMembershipChanged():");

    GroupCall groupCall = currentState.getCallInfoState().requireGroupCall();
    PeekInfo  peekInfo  = groupCall.getPeekInfo();

    if (peekInfo == null) {
      return currentState;
    }

    if (currentState.getCallSetupState().hasSentJoinedMessage()) {
      return currentState;
    }

    webRtcInteractor.sendGroupCallMessage(currentState.getCallInfoState().getCallRecipient(), WebRtcUtil.getGroupCallEraId(groupCall));

    return currentState.builder()
                       .changeCallSetupState()
                       .sentJoinedMessage(true)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    Log.i(TAG, "handleLocalHangup():");

    GroupCall groupCall = currentState.getCallInfoState().requireGroupCall();

    try {
      groupCall.disconnect();
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to disconnect from group call", e);
    }

    webRtcInteractor.sendGroupCallMessage(currentState.getCallInfoState().getCallRecipient(), WebRtcUtil.getGroupCallEraId(groupCall));

    currentState = currentState.builder()
                               .changeCallInfoState()
                               .callState(WebRtcViewModel.State.CALL_DISCONNECTED)
                               .groupCallState(WebRtcViewModel.GroupCallState.DISCONNECTED)
                               .build();

    webRtcInteractor.sendMessage(currentState);

    return terminateGroupCall(currentState);
  }
}
