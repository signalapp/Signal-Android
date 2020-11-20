package org.thoughtcrime.securesms.service.webrtc;

import android.util.LongSparseArray;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.signal.ringrtc.CallException;
import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.webrtc.VideoTrack;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base group call action processor that handles general callbacks around call members
 * and call specific setup information that is the same for any group call state.
 */
public class GroupActionProcessor extends DeviceAwareActionProcessor {
  public GroupActionProcessor(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    super(webRtcInteractor, tag);
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupRemoteDeviceStateChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupRemoteDeviceStateChanged():");

    GroupCall                               groupCall    = currentState.getCallInfoState().requireGroupCall();
    Map<CallParticipantId, CallParticipant> participants = currentState.getCallInfoState().getRemoteCallParticipantsMap();

    WebRtcServiceStateBuilder.CallInfoStateBuilder builder = currentState.builder()
                                                                         .changeCallInfoState()
                                                                         .clearParticipantMap();

    LongSparseArray<GroupCall.RemoteDeviceState> remoteDevices = groupCall.getRemoteDeviceStates();

    for (int i = 0; i < remoteDevices.size(); i++) {
      GroupCall.RemoteDeviceState device            = remoteDevices.get(remoteDevices.keyAt(i));
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
                             CallParticipant.createRemote(recipient,
                                                          null,
                                                          videoSink,
                                                          Boolean.FALSE.equals(device.getAudioMuted()),
                                                          Boolean.FALSE.equals(device.getVideoMuted())));
    }

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
    webRtcInteractor.updatePhoneState(LockManager.PhoneState.PROCESSING);
    webRtcInteractor.stopForegroundService();
    boolean playDisconnectSound = currentState.getCallInfoState().getCallState() == WebRtcViewModel.State.CALL_DISCONNECTED;
    webRtcInteractor.stopAudio(playDisconnectSound);
    webRtcInteractor.setWantsBluetoothConnection(false);

    webRtcInteractor.updatePhoneState(LockManager.PhoneState.IDLE);

    WebRtcVideoUtil.deinitializeVideo(currentState);

    return new WebRtcServiceState(new IdleActionProcessor(webRtcInteractor));
  }
}
