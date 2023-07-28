package org.thoughtcrime.securesms.service.webrtc;

import android.util.LongSparseArray;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.VideoState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.webrtc.PeerConnection;
import org.webrtc.VideoTrack;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base group call action processor that handles general callbacks around call members
 * and call specific setup information that is the same for any group call state.
 */
public class GroupActionProcessor extends DeviceAwareActionProcessor {

  protected MultiPeerActionProcessorFactory actionProcessorFactory;

  public GroupActionProcessor(@NonNull MultiPeerActionProcessorFactory actionProcessorFactory,
                              @NonNull WebRtcInteractor webRtcInteractor,
                              @NonNull String tag)
  {
    super(webRtcInteractor, tag);
    this.actionProcessorFactory = actionProcessorFactory;
  }

  protected @NonNull WebRtcServiceState handleReceivedOffer(@NonNull WebRtcServiceState currentState,
                                                            @NonNull WebRtcData.CallMetadata callMetadata,
                                                            @NonNull WebRtcData.OfferMetadata offerMetadata,
                                                            @NonNull WebRtcData.ReceivedOfferMetadata receivedOfferMetadata)
  {
    Log.i(tag, "handleReceivedOffer(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    Log.i(tag, "In a group call, send busy back to 1:1 call offer.");
    currentState.getActionProcessor().handleSendBusy(currentState, callMetadata, true);
    webRtcInteractor.insertMissedCall(callMetadata.getRemotePeer(), receivedOfferMetadata.getServerReceivedTimestamp(), offerMetadata.getOfferType() == OfferMessage.Type.VIDEO_CALL);

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupRemoteDeviceStateChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupRemoteDeviceStateChanged():");

    GroupCall                               groupCall    = currentState.getCallInfoState().requireGroupCall();
    Map<CallParticipantId, CallParticipant> participants = currentState.getCallInfoState().getRemoteCallParticipantsMap();

    LongSparseArray<GroupCall.RemoteDeviceState> remoteDevices = groupCall.getRemoteDeviceStates();

    if (remoteDevices == null) {
      Log.w(tag, "Unable to update remote devices with null list.");
      return currentState;
    }

    WebRtcServiceStateBuilder.CallInfoStateBuilder builder = currentState.builder()
                                                                         .changeCallInfoState()
                                                                         .clearParticipantMap();

    List<GroupCall.RemoteDeviceState> remoteDeviceStates = new ArrayList<>(remoteDevices.size());
    for (int i = 0; i < remoteDevices.size(); i++) {
      remoteDeviceStates.add(remoteDevices.get(remoteDevices.keyAt(i)));
    }
    Collections.sort(remoteDeviceStates, (a, b) -> Long.compare(a.getAddedTime(), b.getAddedTime()));

    Set<Recipient> seen = new HashSet<>();
    seen.add(Recipient.self());

    for (GroupCall.RemoteDeviceState device : remoteDeviceStates) {
      Recipient                   recipient         = Recipient.externalPush(ACI.from(device.getUserId()));
      CallParticipantId           callParticipantId = new CallParticipantId(device.getDemuxId(), recipient.getId());
      CallParticipant             callParticipant   = participants.get(callParticipantId);

      BroadcastVideoSink videoSink;
      VideoTrack         videoTrack = device.getVideoTrack();
      if (videoTrack != null) {
        videoSink = (callParticipant != null && callParticipant.getVideoSink().getLockableEglBase().getEglBase() != null) ? callParticipant.getVideoSink()
                                                                                                                          : new BroadcastVideoSink(currentState.getVideoState().getLockableEglBase(),
                                                                                                                                                   true,
                                                                                                                                                   true,
                                                                                                                                                   currentState.getLocalDeviceState().getOrientation().getDegrees());
        videoTrack.addSink(videoSink);
      } else {
        videoSink = new BroadcastVideoSink();
      }

      builder.putParticipant(callParticipantId,
                             CallParticipant.createRemote(callParticipantId,
                                                          recipient,
                                                          null,
                                                          videoSink,
                                                          device.getForwardingVideo() == null || device.getForwardingVideo(),
                                                          Boolean.FALSE.equals(device.getAudioMuted()),
                                                          Boolean.FALSE.equals(device.getVideoMuted()),
                                                          device.getSpeakerTime(),
                                                          device.getMediaKeysReceived(),
                                                          device.getAddedTime(),
                                                          Boolean.TRUE.equals(device.getPresenting()),
                                                          seen.contains(recipient) ? CallParticipant.DeviceOrdinal.SECONDARY
                                                                                   : CallParticipant.DeviceOrdinal.PRIMARY));

      seen.add(recipient);
    }

    builder.remoteDevicesCount(remoteDevices.size());

    return builder.build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupRequestMembershipProof(@NonNull WebRtcServiceState currentState, int groupCallHashCode) {
    Log.i(tag, "handleGroupRequestMembershipProof():");
    Recipient recipient = currentState.getCallInfoState().getCallRecipient();
    if (!recipient.isPushV2Group()) {
      Log.i(tag, "Request membership proof for non-group");
      return currentState;
    }

    GroupCall currentGroupCall = currentState.getCallInfoState().getGroupCall();

    if (currentGroupCall == null || currentGroupCall.hashCode() != groupCallHashCode) {
      Log.i(tag, "Skipping group membership proof request, requested group call does not match current group call");
      return currentState;
    }

    //noinspection OptionalGetWithoutIsPresent
    webRtcInteractor.requestGroupMembershipProof(recipient.getGroupId().get().requireV2(), groupCallHashCode);

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupMembershipProofResponse(@NonNull WebRtcServiceState currentState, int groupCallHash, @NonNull byte[] groupMembershipToken) {
    Log.i(tag, "handleGroupMembershipProofResponse():");

    GroupCall groupCall = currentState.getCallInfoState().getGroupCall();

    if (groupCall == null || groupCall.hashCode() != groupCallHash) {
      return currentState;
    }

    try {
      groupCall.setMembershipProof(groupMembershipToken);
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to set group membership proof", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupRequestUpdateMembers(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupRequestUpdateMembers():");

    Recipient group     = currentState.getCallInfoState().getCallRecipient();
    GroupCall groupCall = currentState.getCallInfoState().requireGroupCall();

    List<GroupCall.GroupMemberInfo> members = Stream.of(GroupManager.getUuidCipherTexts(context, group.requireGroupId().requireV2()))
                                                    .map(entry -> new GroupCall.GroupMemberInfo(entry.getKey(), entry.getValue().serialize()))
                                                    .toList();

    try {
      groupCall.setGroupMembers(new ArrayList<>(members));
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable set group members", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleUpdateRenderedResolutions(@NonNull WebRtcServiceState currentState) {
    Map<CallParticipantId, CallParticipant> participants = currentState.getCallInfoState().getRemoteCallParticipantsMap();

    ArrayList<GroupCall.VideoRequest> resolutionRequests = new ArrayList<>(participants.size());
    for (Map.Entry<CallParticipantId, CallParticipant> entry : participants.entrySet()) {
      BroadcastVideoSink               videoSink = entry.getValue().getVideoSink();
      BroadcastVideoSink.RequestedSize maxSize   = videoSink.getMaxRequestingSize();

      resolutionRequests.add(new GroupCall.VideoRequest(entry.getKey().getDemuxId(), maxSize.getWidth(), maxSize.getHeight(), null));
      videoSink.setCurrentlyRequestedMaxSize(maxSize);
    }

    try {
      currentState.getCallInfoState().requireGroupCall().requestVideo(resolutionRequests, 0);
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to set rendered resolutions", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetRingGroup(@NonNull WebRtcServiceState currentState, boolean ringGroup) {
    Log.i(tag, "handleSetRingGroup(): ring: " + ringGroup);

    if (currentState.getCallSetupState(RemotePeer.GROUP_CALL_ID).shouldRingGroup() == ringGroup) {
      return currentState;
    }

    return currentState.builder()
                       .changeCallSetupState(RemotePeer.GROUP_CALL_ID)
                       .setRingGroup(ringGroup)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupMessageSentError(@NonNull WebRtcServiceState currentState,
                                                                    @NonNull Collection<RecipientId> recipientIds,
                                                                    @NonNull WebRtcViewModel.State errorCallState)
  {
    Log.w(tag, "handleGroupMessageSentError(): error: " + errorCallState);

    if (errorCallState == WebRtcViewModel.State.UNTRUSTED_IDENTITY) {
      return currentState.builder()
                         .changeCallInfoState()
                         .addIdentityChangedRecipients(recipientIds)
                         .build();
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupApproveSafetyNumberChange(@NonNull WebRtcServiceState currentState,
                                                                             @NonNull List<RecipientId> recipientIds)
  {
    Log.i(tag, "handleGroupApproveSafetyNumberChange():");

    GroupCall groupCall = currentState.getCallInfoState().getGroupCall();

    if (groupCall != null) {
      currentState = currentState.builder()
                                 .changeCallInfoState()
                                 .removeIdentityChangedRecipients(recipientIds)
                                 .build();

      try {
        groupCall.resendMediaKeys();
      } catch (CallException e) {
        return groupCallFailure(currentState, "Unable to resend media keys", e);
      }
    }

    return currentState;
  }

  @Override protected @NonNull WebRtcServiceState handleGroupLocalDeviceStateChanged(@NonNull WebRtcServiceState currentState) {
    GroupCall                  groupCall = currentState.getCallInfoState().requireGroupCall();
    PeerConnection.AdapterType type      = groupCall.getLocalDeviceState().getNetworkRoute().getLocalAdapterType();

    return currentState.builder()
                       .changeLocalDeviceState()
                       .setNetworkConnectionType(type)
                       .commit()
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupCallEnded(@NonNull WebRtcServiceState currentState, int groupCallHash, @NonNull GroupCall.GroupCallEndReason groupCallEndReason) {
    Log.i(tag, "handleGroupCallEnded(): reason: " + groupCallEndReason);

    GroupCall groupCall = currentState.getCallInfoState().getGroupCall();

    if (groupCall == null || groupCall.hashCode() != groupCallHash) {
      return currentState;
    }

    try {
      groupCall.disconnect();
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to disconnect from group call", e);
    }

    if (groupCallEndReason != GroupCall.GroupCallEndReason.DEVICE_EXPLICITLY_DISCONNECTED) {
      Log.i(tag, "Group call ended unexpectedly, reinitializing and dropping back to lobby");
      Recipient  currentRecipient = currentState.getCallInfoState().getCallRecipient();
      VideoState videoState       = currentState.getVideoState();

      currentState = terminateGroupCall(currentState, false).builder()
                                                            .actionProcessor(actionProcessorFactory.createNetworkUnavailableActionProcessor(webRtcInteractor))
                                                            .changeVideoState()
                                                            .eglBase(videoState.getLockableEglBase())
                                                            .camera(videoState.getCamera())
                                                            .localSink(videoState.getLocalSink())
                                                            .commit()
                                                            .changeCallInfoState()
                                                            .callState(WebRtcViewModel.State.CALL_PRE_JOIN)
                                                            .callRecipient(currentRecipient)
                                                            .build();

      currentState = WebRtcVideoUtil.initializeVanityCamera(WebRtcVideoUtil.reinitializeCamera(context, webRtcInteractor.getCameraEventListener(), currentState));

      return currentState.getActionProcessor().handlePreJoinCall(currentState, new RemotePeer(currentRecipient.getId()));
    }

    currentState = currentState.builder()
                               .changeCallInfoState()
                               .callState(WebRtcViewModel.State.CALL_DISCONNECTED)
                               .groupCallState(WebRtcViewModel.GroupCallState.DISCONNECTED)
                               .build();

    webRtcInteractor.postStateUpdate(currentState);

    return terminateGroupCall(currentState);
  }
}
