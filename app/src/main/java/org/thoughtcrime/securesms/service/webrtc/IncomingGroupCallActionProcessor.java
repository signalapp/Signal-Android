package org.thoughtcrime.securesms.service.webrtc;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.components.webrtc.EglBaseWrapper;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.DoNotDisturbUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.NetworkUtil;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;

import java.util.Optional;

import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_CONNECTING;
import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_RINGING;

/**
 * Process actions to go from incoming "ringing" group call to joining. By the time this processor
 * is running, the group call to ring has been verified to have at least one active device.
 */
public final class IncomingGroupCallActionProcessor extends DeviceAwareActionProcessor {

  private static final String TAG = Log.tag(IncomingGroupCallActionProcessor.class);

  public IncomingGroupCallActionProcessor(WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
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

    Recipient recipient              = remotePeerGroup.getRecipient();
    boolean   updateForCurrentRingId = ringId == currentState.getCallSetupState(RemotePeer.GROUP_CALL_ID).getRingId();
    boolean   isCurrentlyRinging     = currentState.getCallInfoState().getGroupCallState().isRinging();

    if (SignalDatabase.calls().isRingCancelled(ringId, remotePeerGroup.getId()) && !updateForCurrentRingId) {
      try {
        Log.i(TAG, "Ignoring incoming ring request for already cancelled ring: " + ringId);
        webRtcInteractor.getCallManager().cancelGroupRing(groupId.getDecodedId(), ringId, null);
      } catch (CallException e) {
        Log.w(TAG, "Error while trying to cancel ring: " + ringId, e);
      }
      return currentState;
    }

    if (ringUpdate != CallManager.RingUpdate.REQUESTED) {
      SignalDatabase.calls().insertOrUpdateGroupCallFromRingState(ringId,
                                                                  remotePeerGroup.getId(),
                                                                  sender,
                                                                  System.currentTimeMillis(),
                                                                  ringUpdate);

      if (updateForCurrentRingId && isCurrentlyRinging) {
        Log.i(TAG, "Cancelling current ring: " + ringId);

        currentState = currentState.builder()
                                   .changeCallInfoState()
                                   .callState(WebRtcViewModel.State.CALL_DISCONNECTED)
                                   .build();

        webRtcInteractor.postStateUpdate(currentState);

        return terminateGroupCall(currentState);
      } else {
        return currentState;
      }
    }

    if (!updateForCurrentRingId && isCurrentlyRinging) {
      try {
        Log.i(TAG, "Already ringing so reply busy for new ring: " + ringId);
        webRtcInteractor.getCallManager().cancelGroupRing(groupId.getDecodedId(), ringId, CallManager.RingCancelReason.Busy);
      } catch (CallException e) {
        Log.w(TAG, "Error while trying to cancel ring: " + ringId, e);
      }
      return currentState;
    }

    if (updateForCurrentRingId) {
      Log.i(TAG, "Already ringing for ring: " + ringId);
      return currentState;
    }

    Log.i(TAG, "Requesting new ring: " + ringId);

    Recipient ringerRecipient = Recipient.externalPush(sender);
    SignalDatabase.calls().insertOrUpdateGroupCallFromRingState(
        ringId,
        remotePeerGroup.getId(),
        ringerRecipient.getId(),
        System.currentTimeMillis(),
        ringUpdate
    );

    currentState = WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState, RemotePeer.GROUP_CALL_ID.longValue());

    webRtcInteractor.setCallInProgressNotification(TYPE_INCOMING_RINGING, remotePeerGroup, true);
    webRtcInteractor.initializeAudioForCall();

    boolean shouldDisturbUserWithCall = DoNotDisturbUtil.shouldDisturbUserWithCall(context.getApplicationContext());
    if (shouldDisturbUserWithCall) {
      webRtcInteractor.updatePhoneState(LockManager.PhoneState.INTERACTIVE);
      boolean started = webRtcInteractor.startWebRtcCallActivityIfPossible();
      if (!started) {
        Log.i(TAG, "Unable to start call activity due to OS version or not being in the foreground");
        AppForegroundObserver.addListener(webRtcInteractor.getForegroundListener());
      }
    }

    if (shouldDisturbUserWithCall && SignalStore.settings().isCallNotificationsEnabled()) {
      Uri                         ringtone     = recipient.resolve().getCallRingtone();
      RecipientTable.VibrateState vibrateState = recipient.resolve().getCallVibrate();

      if (ringtone == null) {
        ringtone = SignalStore.settings().getCallRingtone();
      }

      webRtcInteractor.startIncomingRinger(ringtone, vibrateState == RecipientTable.VibrateState.ENABLED || (vibrateState == RecipientTable.VibrateState.DEFAULT && SignalStore.settings().isCallVibrateEnabled()));
    }

    webRtcInteractor.registerPowerButtonReceiver();

    return currentState.builder()
                       .changeCallSetupState(RemotePeer.GROUP_CALL_ID)
                       .isRemoteVideoOffer(true)
                       .ringId(ringId)
                       .ringerRecipient(ringerRecipient)
                       .commit()
                       .changeCallInfoState()
                       .activePeer(new RemotePeer(currentState.getCallInfoState().getCallRecipient().getId(), RemotePeer.GROUP_CALL_ID))
                       .callRecipient(remotePeerGroup.getRecipient())
                       .callState(WebRtcViewModel.State.CALL_INCOMING)
                       .groupCallState(WebRtcViewModel.GroupCallState.RINGING)
                       .putParticipant(remotePeerGroup.getRecipient(),
                                       CallParticipant.createRemote(new CallParticipantId(remotePeerGroup.getRecipient()),
                                                                    remotePeerGroup.getRecipient(),
                                                                    null,
                                                                    new BroadcastVideoSink(currentState.getVideoState().getLockableEglBase(),
                                                                                           true,
                                                                                           true,
                                                                                           currentState.getLocalDeviceState().getOrientation().getDegrees()),
                                                                    true,
                                                                    true,
                                                                    false,
                                                                    CallParticipant.HAND_LOWERED,
                                                                    0,
                                                                    true,
                                                                    0,
                                                                    false,
                                                                    CallParticipant.DeviceOrdinal.PRIMARY
                                       ))
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleAcceptCall(@NonNull WebRtcServiceState currentState, boolean answerWithVideo) {
    byte[] groupId = currentState.getCallInfoState().getCallRecipient().requireGroupId().getDecodedId();
    GroupCall groupCall = webRtcInteractor.getCallManager().createGroupCall(groupId,
                                                                            SignalStore.internal().groupCallingServer(),
                                                                            new byte[0],
                                                                            AUDIO_LEVELS_INTERVAL,
                                                                            RingRtcDynamicConfiguration.getAudioProcessingMethod(),
                                                                            RingRtcDynamicConfiguration.shouldUseOboeAdm(),
                                                                            webRtcInteractor.getGroupCallObserver());

    try {
      groupCall.setOutgoingAudioMuted(true);
      groupCall.setOutgoingVideoMuted(true);
      groupCall.setDataMode(NetworkUtil.getCallingDataMode(context, groupCall.getLocalDeviceState().getNetworkRoute().getLocalAdapterType()));

      Log.i(TAG, "Connecting to group call: " + currentState.getCallInfoState().getCallRecipient().getId());
      groupCall.connect();
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to connect to group call", e);
    }

    currentState = currentState.builder()
                               .changeCallInfoState()
                               .groupCall(groupCall)
                               .groupCallState(WebRtcViewModel.GroupCallState.DISCONNECTED)
                               .commit()
                               .changeCallSetupState(RemotePeer.GROUP_CALL_ID)
                               .isRemoteVideoOffer(false)
                               .enableVideoOnCreate(answerWithVideo)
                               .build();

    webRtcInteractor.setCallInProgressNotification(TYPE_INCOMING_CONNECTING, currentState.getCallInfoState().getCallRecipient(), true);
    webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context));
    webRtcInteractor.initializeAudioForCall();

    try {
      groupCall.setOutgoingVideoSource(currentState.getVideoState().requireLocalSink(), currentState.getVideoState().requireCamera());
      groupCall.setOutgoingVideoMuted(answerWithVideo);
      groupCall.setOutgoingAudioMuted(!currentState.getLocalDeviceState().isMicrophoneEnabled());
      groupCall.setDataMode(NetworkUtil.getCallingDataMode(context, groupCall.getLocalDeviceState().getNetworkRoute().getLocalAdapterType()));

      groupCall.join();
    } catch (CallException e) {
      return groupCallFailure(currentState, "Unable to join group call", e);
    }

    return currentState.builder()
                       .actionProcessor(MultiPeerActionProcessorFactory.GroupActionProcessorFactory.INSTANCE.createJoiningActionProcessor(webRtcInteractor))
                       .changeCallInfoState()
                       .callState(WebRtcViewModel.State.CALL_OUTGOING)
                       .groupCallState(WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINING)
                       .commit()
                       .changeLocalDeviceState()
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleDenyCall(@NonNull WebRtcServiceState currentState) {
    Log.i(TAG, "handleDenyCall():");

    Recipient         recipient = currentState.getCallInfoState().getCallRecipient();
    Optional<GroupId> groupId   = recipient.getGroupId();
    long              ringId    = currentState.getCallSetupState(RemotePeer.GROUP_CALL_ID).getRingId();
    Recipient         ringer    = currentState.getCallSetupState(RemotePeer.GROUP_CALL_ID).getRingerRecipient();

    SignalDatabase.calls().insertOrUpdateGroupCallFromRingState(ringId,
                                                                recipient.getId(),
                                                                ringer.getId(),
                                                                System.currentTimeMillis(),
                                                                CallManager.RingUpdate.DECLINED_ON_ANOTHER_DEVICE);

    try {
      webRtcInteractor.getCallManager().cancelGroupRing(groupId.get().getDecodedId(),
                                                        ringId,
                                                        CallManager.RingCancelReason.DeclinedByUser);
    } catch (CallException e) {
      Log.w(TAG, "Error while trying to cancel ring " + ringId, e);
    }

    CallId     callId     = new CallId(ringId);
    RemotePeer remotePeer = new RemotePeer(recipient.getId(), callId);

    webRtcInteractor.sendGroupCallNotAcceptedCallEventSyncMessage(remotePeer, false);
    webRtcInteractor.sendGroupCallMessage(currentState.getCallInfoState().getCallRecipient(), null, callId, true, false);
    webRtcInteractor.updatePhoneState(LockManager.PhoneState.PROCESSING);
    webRtcInteractor.stopAudio(false);
    webRtcInteractor.updatePhoneState(LockManager.PhoneState.IDLE);
    webRtcInteractor.stopForegroundService();

    currentState = WebRtcVideoUtil.deinitializeVideo(currentState);
    EglBaseWrapper.releaseEglBase(RemotePeer.GROUP_CALL_ID.longValue());

    return currentState.builder()
                       .actionProcessor(new IdleActionProcessor(webRtcInteractor))
                       .terminate(RemotePeer.GROUP_CALL_ID)
                       .build();
  }
}
