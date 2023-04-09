package org.thoughtcrime.securesms.service.webrtc;

import android.os.ResultReceiver;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.GroupCall;
import org.signal.ringrtc.PeekInfo;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcEphemeralState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Process actions for when the call has at least once been connected and joined.
 */
public class GroupConnectedActionProcessor extends GroupActionProcessor {

  private static final String TAG = Log.tag(GroupConnectedActionProcessor.class);

  public GroupConnectedActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
  }

  @Override
  protected @NonNull WebRtcServiceState handleIsInCallQuery(@NonNull WebRtcServiceState currentState, @Nullable ResultReceiver resultReceiver) {
    if (resultReceiver != null) {
      resultReceiver.send(1, null);
    }
    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupLocalDeviceStateChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupLocalDeviceStateChanged():");

    currentState = super.handleGroupLocalDeviceStateChanged(currentState);

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

    WebRtcUtil.enableSpeakerPhoneIfNeeded(webRtcInteractor, currentState);

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
  protected @NonNull WebRtcEphemeralState handleGroupAudioLevelsChanged(@NonNull WebRtcServiceState currentState, @NonNull WebRtcEphemeralState ephemeralState) {
    GroupCall                                    groupCall          = currentState.getCallInfoState().requireGroupCall();
    LongSparseArray<GroupCall.RemoteDeviceState> remoteDeviceStates = groupCall.getRemoteDeviceStates();

    CallParticipant.AudioLevel localAudioLevel = CallParticipant.AudioLevel.fromRawAudioLevel(groupCall.getLocalDeviceState().getAudioLevel());

    HashMap<CallParticipantId, CallParticipant.AudioLevel> remoteAudioLevels = new HashMap<>();
    for (CallParticipant participant : currentState.getCallInfoState().getRemoteCallParticipants()) {
      CallParticipantId callParticipantId = participant.getCallParticipantId();

      if (remoteDeviceStates != null) {
        GroupCall.RemoteDeviceState state = remoteDeviceStates.get(callParticipantId.getDemuxId());
        if (state != null) {
          remoteAudioLevels.put(callParticipantId, CallParticipant.AudioLevel.fromRawAudioLevel(state.getAudioLevel()));
        }
      }
    }

    return ephemeralState.copy(localAudioLevel, remoteAudioLevels);
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupJoinedMembershipChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupJoinedMembershipChanged():");

    GroupCall groupCall = currentState.getCallInfoState().requireGroupCall();
    PeekInfo  peekInfo  = groupCall.getPeekInfo();

    if (peekInfo == null) {
      return currentState;
    }

    if (currentState.getCallSetupState(RemotePeer.GROUP_CALL_ID).hasSentJoinedMessage()) {
      return currentState;
    }

    boolean remoteUserRangTheCall = currentState.getCallSetupState(RemotePeer.GROUP_CALL_ID).getRingerRecipient() != Recipient.self();
    String  eraId                 = WebRtcUtil.getGroupCallEraId(groupCall);
    webRtcInteractor.sendGroupCallMessage(currentState.getCallInfoState().getCallRecipient(), eraId, remoteUserRangTheCall, true);

    List<UUID> members = new ArrayList<>(peekInfo.getJoinedMembers());
    if (!members.contains(SignalStore.account().requireAci().uuid())) {
      members.add(SignalStore.account().requireAci().uuid());
    }
    webRtcInteractor.updateGroupCallUpdateMessage(currentState.getCallInfoState().getCallRecipient().getId(), eraId, members, WebRtcUtil.isCallFull(peekInfo));

    return currentState.builder()
                       .changeCallSetupState(RemotePeer.GROUP_CALL_ID)
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

    String eraId = WebRtcUtil.getGroupCallEraId(groupCall);
    webRtcInteractor.sendGroupCallMessage(currentState.getCallInfoState().getCallRecipient(), eraId, false, false);

    List<UUID> members = Stream.of(currentState.getCallInfoState().getRemoteCallParticipants()).map(p -> p.getRecipient().requireServiceId().uuid()).toList();
    webRtcInteractor.updateGroupCallUpdateMessage(currentState.getCallInfoState().getCallRecipient().getId(), eraId, members, false);

    currentState = currentState.builder()
                               .changeCallInfoState()
                               .callState(WebRtcViewModel.State.CALL_DISCONNECTED)
                               .groupCallState(WebRtcViewModel.GroupCallState.DISCONNECTED)
                               .build();

    webRtcInteractor.postStateUpdate(currentState);

    return terminateGroupCall(currentState);
  }
}
