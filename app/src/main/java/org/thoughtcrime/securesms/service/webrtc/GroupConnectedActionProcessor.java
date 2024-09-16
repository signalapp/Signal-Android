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
import org.thoughtcrime.securesms.events.GroupCallReactionEvent;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.CallInfoState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcEphemeralState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Process actions for when the call has at least once been connected and joined.
 */
public class GroupConnectedActionProcessor extends GroupActionProcessor {

  private static final String TAG = Log.tag(GroupConnectedActionProcessor.class);

  public GroupConnectedActionProcessor(@NonNull MultiPeerActionProcessorFactory actionProcessorFactory, @NonNull WebRtcInteractor webRtcInteractor) {
    this(actionProcessorFactory, webRtcInteractor, TAG);
  }

  protected GroupConnectedActionProcessor(@NonNull MultiPeerActionProcessorFactory actionProcessorFactory, @NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    super(actionProcessorFactory, webRtcInteractor, tag);
  }

  @Override
  protected @NonNull WebRtcServiceState handleIsInCallQuery(@NonNull WebRtcServiceState currentState, @Nullable ResultReceiver resultReceiver) {
    if (resultReceiver != null) {
      resultReceiver.send(1, ActiveCallData.fromCallState(currentState).toBundle());
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
      } else if (joinState == GroupCall.JoinState.JOINING || joinState == GroupCall.JoinState.PENDING) {
        groupCallState = WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINING;
      }
    }

    return currentState.builder().changeCallInfoState()
                       .groupCallState(groupCallState)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(tag, "handleSetEnableVideo():");

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

    return ephemeralState.copy(localAudioLevel, remoteAudioLevels, ephemeralState.getUnexpiredReactions());
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
    webRtcInteractor.sendGroupCallMessage(currentState.getCallInfoState().getCallRecipient(), eraId, null, remoteUserRangTheCall, true);

    List<UUID> members = new ArrayList<>(peekInfo.getJoinedMembers());
    if (!members.contains(SignalStore.account().requireAci().getRawUuid())) {
      members.add(SignalStore.account().requireAci().getRawUuid());
    }
    webRtcInteractor.updateGroupCallUpdateMessage(currentState.getCallInfoState().getCallRecipient().getId(), eraId, members, WebRtcUtil.isCallFull(peekInfo));

    return currentState.builder()
                       .changeCallSetupState(RemotePeer.GROUP_CALL_ID)
                       .sentJoinedMessage(true)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleLocalHangup():");

    GroupCall groupCall = currentState.getCallInfoState().requireGroupCall();

    try {
      groupCall.disconnect();
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to disconnect from group call", e);
    }

    String eraId = WebRtcUtil.getGroupCallEraId(groupCall);
    webRtcInteractor.sendGroupCallMessage(currentState.getCallInfoState().getCallRecipient(), eraId, null, false, false);

    List<UUID> members = Stream.of(currentState.getCallInfoState().getRemoteCallParticipants()).map(p -> p.getRecipient().requireServiceId().getRawUuid()).toList();
    webRtcInteractor.updateGroupCallUpdateMessage(currentState.getCallInfoState().getCallRecipient().getId(), eraId, members, false);

    currentState = currentState.builder()
                               .changeCallInfoState()
                               .callState(WebRtcViewModel.State.CALL_DISCONNECTED)
                               .groupCallState(WebRtcViewModel.GroupCallState.DISCONNECTED)
                               .build();

    webRtcInteractor.postStateUpdate(currentState);

    return terminateGroupCall(currentState);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSelfRaiseHand(@NonNull WebRtcServiceState currentState, boolean raised) {
    Log.i(tag, "handleSelfRaiseHand():");
    try {
      currentState.getCallInfoState().requireGroupCall().raiseHand(raised);

      return currentState;
    } catch (CallException e) {
      Log.w(TAG, "Unable to " + (raised ? "raise" : "lower") + " hand in group call", e);
    }
    return currentState;
  }

  @Override
  protected @NonNull WebRtcEphemeralState handleSendGroupReact(@NonNull WebRtcServiceState currentState, @NonNull WebRtcEphemeralState ephemeralState, @NonNull String reaction) {
    try {
      currentState.getCallInfoState().requireGroupCall().react(reaction);

      List<GroupCallReactionEvent> reactionList  = ephemeralState.getUnexpiredReactions();
      reactionList.add(new GroupCallReactionEvent(Recipient.self(), reaction, System.currentTimeMillis()));

      return ephemeralState.copy(ephemeralState.getLocalAudioLevel(), ephemeralState.getRemoteAudioLevels(), reactionList);
    } catch (CallException e) {
      Log.w(TAG,"Unable to send reaction in group call", e);
    }
    return ephemeralState;
  }

  @Override
  protected @NonNull WebRtcEphemeralState handleGroupCallReaction(@NonNull WebRtcServiceState currentState, @NonNull WebRtcEphemeralState ephemeralState, List<GroupCall.Reaction> reactions) {
    List<GroupCallReactionEvent> reactionList  = ephemeralState.getUnexpiredReactions();
    List<CallParticipant>        participants  = currentState.getCallInfoState().getRemoteCallParticipants();

    for (GroupCall.Reaction reaction : reactions) {
      final GroupCallReactionEvent event = createGroupCallReaction(participants, reaction);
      if (event != null) {
        reactionList.add(event);
      }
    }

    return ephemeralState.copy(ephemeralState.getLocalAudioLevel(), ephemeralState.getRemoteAudioLevels(), reactionList);
  }

  @Nullable
  private GroupCallReactionEvent createGroupCallReaction(Collection<CallParticipant> participants, final GroupCall.Reaction reaction) {
    CallParticipant participant = participants.stream().filter(it -> it.getCallParticipantId().getDemuxId() == reaction.demuxId).findFirst().orElse(null);
    if (participant == null) {
      Log.v(TAG, "Could not find CallParticipantId in list of call participants based on demuxId for reaction.");
      return null;
    }

    return new GroupCallReactionEvent(participant.getRecipient(), reaction.value, System.currentTimeMillis());
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupCallRaisedHand(@NonNull WebRtcServiceState currentState, List<Long> raisedHands) {
    Log.i(TAG, "handleGroupCallRaisedHand():");

    boolean                                        playSound       = !raisedHands.isEmpty();
    long                                           now             = System.currentTimeMillis();
    WebRtcServiceStateBuilder.CallInfoStateBuilder callInfoBuilder = currentState.builder().changeCallInfoState();
    Long                                           localDemuxId    = currentState.getCallInfoState().requireGroupCall().getLocalDeviceState().getDemuxId();

    List<CallParticipant> participants = currentState.getCallInfoState().getRemoteCallParticipants();

    for (CallParticipant updatedParticipant : participants) {
      int raisedHandIndex = raisedHands.indexOf(updatedParticipant.getCallParticipantId().getDemuxId());
      boolean wasHandAlreadyRaised  = updatedParticipant.isHandRaised();

      if (wasHandAlreadyRaised) {
        playSound = false;
      }
      
      if (raisedHandIndex >= 0 && !wasHandAlreadyRaised) {
        callInfoBuilder.putParticipant(updatedParticipant.getCallParticipantId(), updatedParticipant.withHandRaisedTimestamp(now + raisedHandIndex));
      } else if (raisedHandIndex < 0 && wasHandAlreadyRaised) {
        callInfoBuilder.putParticipant(updatedParticipant.getCallParticipantId(), updatedParticipant.withHandRaisedTimestamp(CallParticipant.HAND_LOWERED));
      }
    }

    currentState = callInfoBuilder.build();

    if (localDemuxId != null) {
      currentState = currentState.builder()
                                 .changeLocalDeviceState()
                                 .setHandRaisedTimestamp(raisedHands.contains(localDemuxId) ? now : CallParticipant.HAND_LOWERED)
                                 .build();
    }

    if (playSound) {
      webRtcInteractor.playStateChangeUp();
    }

    return currentState;
  }
}
