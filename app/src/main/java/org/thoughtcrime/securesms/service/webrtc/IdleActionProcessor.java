package org.thoughtcrime.securesms.service.webrtc;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.PeekInfo;
import org.thoughtcrime.securesms.components.webrtc.EglBaseWrapper;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile;
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;

/**
 * Action handler for when the system is at rest. Mainly responsible
 * for starting pre-call state, starting an outgoing call, or receiving an
 * incoming call.
 */
public class IdleActionProcessor extends WebRtcActionProcessor {

  private static final String TAG = Log.tag(IdleActionProcessor.class);

  private final BeginCallActionProcessorDelegate beginCallDelegate;

  public IdleActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
    beginCallDelegate = new BeginCallActionProcessorDelegate(webRtcInteractor, TAG);
  }

  protected @NonNull WebRtcServiceState handleStartIncomingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer, @NonNull OfferMessage.Type offerType) {
    Log.i(TAG, "handleStartIncomingCall():");

    currentState = WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState, remotePeer.getCallId().longValue());
    return beginCallDelegate.handleStartIncomingCall(currentState, remotePeer, offerType);
  }

  @Override
  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState,
                                                           @NonNull RemotePeer remotePeer,
                                                           @NonNull OfferMessage.Type offerType)
  {
    Log.i(TAG, "handleOutgoingCall():");

    Recipient recipient = Recipient.resolved(remotePeer.getId());
    if (recipient.isGroup() || recipient.isCallLink()) {
      Log.w(TAG, "Aborting attempt to start 1:1 call for group or call link recipient: " + remotePeer.getId());
      return currentState;
    }

    currentState = WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState, EglBaseWrapper.OUTGOING_PLACEHOLDER);
    return beginCallDelegate.handleOutgoingCall(currentState, remotePeer, offerType);
  }

  @Override
  protected @NonNull WebRtcServiceState handlePreJoinCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handlePreJoinCall():");

    boolean isGroupCall = remotePeer.getRecipient().isPushV2Group() || remotePeer.getRecipient().isCallLink();

    final WebRtcActionProcessor processor;
    if (remotePeer.getRecipient().isCallLink()) {
      processor = MultiPeerActionProcessorFactory.CallLinkActionProcessorFactory.INSTANCE.createPreJoinActionProcessor(webRtcInteractor);
    } else if (remotePeer.getRecipient().isPushV2Group()) {
      processor = MultiPeerActionProcessorFactory.GroupActionProcessorFactory.INSTANCE.createPreJoinActionProcessor(webRtcInteractor);
    } else {
      processor = new PreJoinActionProcessor(webRtcInteractor);
    }

    currentState = WebRtcVideoUtil.initializeVanityCamera(WebRtcVideoUtil.initializeVideo(context,
                                                                                          webRtcInteractor.getCameraEventListener(),
                                                                                          currentState,
                                                                                          isGroupCall ? RemotePeer.GROUP_CALL_ID.longValue()
                                                                                                      : EglBaseWrapper.OUTGOING_PLACEHOLDER));

    currentState = currentState.builder()
                               .actionProcessor(processor)
                               .changeCallInfoState()
                               .callState(WebRtcViewModel.State.CALL_PRE_JOIN)
                               .callRecipient(remotePeer.getRecipient())
                               .build();

    return isGroupCall ? currentState.getActionProcessor().handlePreJoinCall(currentState, remotePeer)
                       : currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleGroupCallRingUpdate(@NonNull WebRtcServiceState currentState,
                                                                  @NonNull RemotePeer remotePeerGroup,
                                                                  @NonNull GroupId.V2 groupId,
                                                                  long ringId,
                                                                  @NonNull ACI sender,
                                                                  @NonNull CallManager.RingUpdate ringUpdate)
  {
    Log.i(TAG, "handleGroupCallRingUpdate(): recipient: " + remotePeerGroup.getId() + " ring: " + ringId + " update: " + ringUpdate);

    int groupSize = remotePeerGroup.getRecipient().getParticipantIds().size();
    if (groupSize > RemoteConfig.maxGroupCallRingSize()) {
      Log.w(TAG, "Received ring request for large group, dropping. size: " + groupSize + " max: " + RemoteConfig.maxGroupCallRingSize());
      return currentState;
    }

    if (ringUpdate != CallManager.RingUpdate.REQUESTED) {
      SignalDatabase.calls().insertOrUpdateGroupCallFromRingState(ringId, remotePeerGroup.getId(), sender, System.currentTimeMillis(), ringUpdate);
      return currentState;
    } else if (SignalDatabase.calls().isRingCancelled(ringId, remotePeerGroup.getId())) {
      try {
        Log.i(TAG, "Incoming ring request for already cancelled ring: " + ringId);
        webRtcInteractor.getCallManager().cancelGroupRing(groupId.getDecodedId(), ringId, null);
      } catch (CallException e) {
        Log.w(TAG, "Error while trying to cancel ring: " + ringId, e);
      }
      return currentState;
    }

    NotificationProfile activeProfile = NotificationProfiles.getActiveProfile(SignalDatabase.notificationProfiles().getProfiles());
    if (activeProfile != null && !(activeProfile.isRecipientAllowed(remotePeerGroup.getId()) || activeProfile.getAllowAllCalls())) {
      try {
        Log.i(TAG, "Incoming ring request for profile restricted recipient");
        SignalDatabase.calls().insertOrUpdateGroupCallFromRingState(ringId, remotePeerGroup.getId(), sender, System.currentTimeMillis(), CallManager.RingUpdate.EXPIRED_REQUEST, true);
        webRtcInteractor.getCallManager().cancelGroupRing(groupId.getDecodedId(), ringId, CallManager.RingCancelReason.DeclinedByUser);
      } catch (CallException e) {
        Log.w(TAG, "Error while trying to cancel ring: " + ringId, e);
      }
      return currentState;
    }

    webRtcInteractor.peekGroupCallForRingingCheck(new GroupCallRingCheckInfo(remotePeerGroup.getId(), groupId, ringId, sender, ringUpdate));

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedGroupCallPeekForRingingCheck(@NonNull WebRtcServiceState currentState, @NonNull GroupCallRingCheckInfo info, @NonNull PeekInfo peekInfo) {
    Log.i(tag, "handleReceivedGroupCallPeekForRingingCheck(): recipient: " + info.getRecipientId() + " ring: " + info.getRingId());

    if (SignalDatabase.calls().isRingCancelled(info.getRingId(), info.getRecipientId())) {
      try {
        Log.i(TAG, "Ring was cancelled while getting peek info ring: " + info.getRingId());
        webRtcInteractor.getCallManager().cancelGroupRing(info.getGroupId().getDecodedId(), info.getRingId(), null);
      } catch (CallException e) {
        Log.w(TAG, "Error while trying to cancel ring: " + info.getRingId(), e);
      }
      return currentState;
    }

    if (peekInfo.getDeviceCount() == 0) {
      Log.i(TAG, "No one in the group call, mark as expired and do not ring");
      SignalDatabase.calls().insertOrUpdateGroupCallFromRingState(info.getRingId(), info.getRecipientId(), info.getRingerAci(), System.currentTimeMillis(), CallManager.RingUpdate.EXPIRED_REQUEST);
      return currentState;
    } else if (peekInfo.getJoinedMembers().contains(Recipient.self().requireServiceId().getRawUuid())) {
      Log.i(TAG, "We are already in the call, mark accepted on another device and do not ring");
      SignalDatabase.calls().insertOrUpdateGroupCallFromRingState(info.getRingId(), info.getRecipientId(), info.getRingerAci(), System.currentTimeMillis(), CallManager.RingUpdate.ACCEPTED_ON_ANOTHER_DEVICE);
      return currentState;
    }

    currentState = currentState.builder()
                               .actionProcessor(new IncomingGroupCallActionProcessor(webRtcInteractor))
                               .build();

    return currentState.getActionProcessor().handleGroupCallRingUpdate(currentState, new RemotePeer(info.getRecipientId()), info.getGroupId(), info.getRingId(), info.getRingerAci(), info.getRingUpdate());
  }
}
