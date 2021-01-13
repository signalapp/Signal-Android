package org.thoughtcrime.securesms.service.webrtc;

import android.util.LongSparseArray;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.components.webrtc.GroupCallSafetyNumberChangeNotificationUtil;
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
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.webrtc.VideoTrack;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.ArrayList;
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
  public GroupActionProcessor(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    super(webRtcInteractor, tag);
  }

  protected @NonNull WebRtcServiceState handleReceivedOffer(@NonNull WebRtcServiceState currentState,
                                                            @NonNull WebRtcData.CallMetadata callMetadata,
                                                            @NonNull WebRtcData.OfferMetadata offerMetadata,
                                                            @NonNull WebRtcData.ReceivedOfferMetadata receivedOfferMetadata)
  {
    Log.i(tag, "handleReceivedOffer(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    Log.i(tag, "In a group call, send busy back to 1:1 call offer.");
    currentState.getActionProcessor().handleSendBusy(currentState, callMetadata, true);
    webRtcInteractor.insertMissedCall(callMetadata.getRemotePeer(), true, receivedOfferMetadata.getServerReceivedTimestamp(), offerMetadata.getOfferType() == OfferMessage.Type.VIDEO_CALL);

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
      Recipient                   recipient         = Recipient.externalPush(context, device.getUserId(), null, false);
      CallParticipantId           callParticipantId = new CallParticipantId(device.getDemuxId(), recipient.getId());
      CallParticipant             callParticipant   = participants.get(callParticipantId);

      BroadcastVideoSink videoSink;
      VideoTrack         videoTrack = device.getVideoTrack();
      if (videoTrack != null) {
        videoSink = (callParticipant != null && callParticipant.getVideoSink().getEglBase() != null) ? callParticipant.getVideoSink()
                                                                                                     : new BroadcastVideoSink(currentState.getVideoState().requireEglBase());
        videoTrack.addSink(videoSink);
      } else {
        videoSink = new BroadcastVideoSink(null);
      }

      builder.putParticipant(callParticipantId,
                             CallParticipant.createRemote(callParticipantId,
                                                          recipient,
                                                          null,
                                                          videoSink,
                                                          Boolean.FALSE.equals(device.getAudioMuted()),
                                                          Boolean.FALSE.equals(device.getVideoMuted()),
                                                          device.getSpeakerTime(),
                                                          device.getMediaKeysReceived(),
                                                          device.getAddedTime(),
                                                          seen.contains(recipient) ? CallParticipant.DeviceOrdinal.SECONDARY
                                                                                   : CallParticipant.DeviceOrdinal.PRIMARY));

      seen.add(recipient);
    }

    builder.remoteDevicesCount(remoteDevices.size());

    return builder.build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupRequestMembershipProof(@NonNull WebRtcServiceState currentState, int groupCallHash, @NonNull byte[] groupMembershipToken) {
    Log.i(tag, "handleGroupRequestMembershipProof():");

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
      videoSink.newSizeRequested();
    }

    try {
      currentState.getCallInfoState().requireGroupCall().requestVideo(resolutionRequests);
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to set rendered resolutions", e);
    }

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleHttpSuccess(@NonNull WebRtcServiceState currentState, @NonNull WebRtcData.HttpData httpData) {
    try {
      webRtcInteractor.getCallManager().receivedHttpResponse(httpData.getRequestId(), httpData.getStatus(), httpData.getBody() != null ? httpData.getBody() : new byte[0]);
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to process received http response", e);
    }
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleHttpFailure(@NonNull WebRtcServiceState currentState, @NonNull WebRtcData.HttpData httpData) {
    try {
      webRtcInteractor.getCallManager().httpRequestFailed(httpData.getRequestId());
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to process received http response", e);
    }
    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleSendOpaqueMessage(@NonNull WebRtcServiceState currentState, @NonNull WebRtcData.OpaqueMessageMetadata opaqueMessageMetadata) {
    Log.i(tag, "handleSendOpaqueMessage():");

    OpaqueMessage            opaqueMessage = new OpaqueMessage(opaqueMessageMetadata.getOpaque());
    SignalServiceCallMessage callMessage   = SignalServiceCallMessage.forOpaque(opaqueMessage, true, null);

    webRtcInteractor.sendOpaqueCallMessage(opaqueMessageMetadata.getUuid(), callMessage);

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedOpaqueMessage(@NonNull WebRtcServiceState currentState, @NonNull WebRtcData.OpaqueMessageMetadata opaqueMessageMetadata) {
    Log.i(tag, "handleReceivedOpaqueMessage():");

    try {
      webRtcInteractor.getCallManager().receivedCallMessage(opaqueMessageMetadata.getUuid(),
                                                            opaqueMessageMetadata.getRemoteDeviceId(),
                                                            1,
                                                            opaqueMessageMetadata.getOpaque(),
                                                            opaqueMessageMetadata.getMessageAgeSeconds());
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to receive opaque message", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupMessageSentError(@NonNull WebRtcServiceState currentState,
                                                                    @NonNull RemotePeer remotePeer,
                                                                    @NonNull WebRtcViewModel.State errorCallState,
                                                                    @NonNull Optional<IdentityKey> identityKey)
  {
    Log.w(tag, "handleGroupMessageSentError(): error: " + errorCallState);

    if (errorCallState == WebRtcViewModel.State.UNTRUSTED_IDENTITY) {
      return currentState.builder()
                         .changeCallInfoState()
                         .addIdentityChangedRecipient(remotePeer.getId())
                         .build();
    }

    return currentState;
  }

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
                                                            .actionProcessor(new GroupNetworkUnavailableActionProcessor(webRtcInteractor))
                                                            .changeVideoState()
                                                            .eglBase(videoState.getEglBase())
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

    webRtcInteractor.sendMessage(currentState);

    return terminateGroupCall(currentState);
  }

  public @NonNull WebRtcServiceState groupCallFailure(@NonNull WebRtcServiceState currentState, @NonNull String message, @NonNull Throwable error) {
    Log.w(tag, "groupCallFailure(): " + message, error);

    GroupCall groupCall = currentState.getCallInfoState().getGroupCall();
    Recipient recipient = currentState.getCallInfoState().getCallRecipient();

    if (recipient != null && currentState.getCallInfoState().getGroupCallState().isConnected()) {
      webRtcInteractor.sendGroupCallMessage(recipient, WebRtcUtil.getGroupCallEraId(groupCall));
    }

    currentState = currentState.builder()
                               .changeCallInfoState()
                               .callState(WebRtcViewModel.State.CALL_DISCONNECTED)
                               .groupCallState(WebRtcViewModel.GroupCallState.DISCONNECTED)
                               .build();

    webRtcInteractor.sendMessage(currentState);

    try {
      if (groupCall != null) {
        groupCall.disconnect();
      }
      webRtcInteractor.getCallManager().reset();
    } catch (CallException e) {
      Log.w(tag, "Unable to reset call manager: ", e);
    }

    return terminateGroupCall(currentState);
  }

  public synchronized @NonNull WebRtcServiceState terminateGroupCall(@NonNull WebRtcServiceState currentState) {
    return terminateGroupCall(currentState, true);
  }

  public synchronized @NonNull WebRtcServiceState terminateGroupCall(@NonNull WebRtcServiceState currentState, boolean terminateVideo) {
    webRtcInteractor.updatePhoneState(LockManager.PhoneState.PROCESSING);
    webRtcInteractor.stopForegroundService();
    boolean playDisconnectSound = currentState.getCallInfoState().getCallState() == WebRtcViewModel.State.CALL_DISCONNECTED;
    webRtcInteractor.stopAudio(playDisconnectSound);
    webRtcInteractor.setWantsBluetoothConnection(false);

    webRtcInteractor.updatePhoneState(LockManager.PhoneState.IDLE);

    if (terminateVideo) {
      WebRtcVideoUtil.deinitializeVideo(currentState);
    }

    GroupCallSafetyNumberChangeNotificationUtil.cancelNotification(context, currentState.getCallInfoState().getCallRecipient());

    return new WebRtcServiceState(new IdleActionProcessor(webRtcInteractor));
  }
}
