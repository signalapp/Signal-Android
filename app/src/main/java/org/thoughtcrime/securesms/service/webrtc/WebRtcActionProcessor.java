package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;
import android.content.Intent;
import android.os.ResultReceiver;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.GroupCall;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.ringrtc.CallState;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.ringrtc.IceCandidateParcel;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.CallMetadata;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.HttpData;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.OfferMetadata;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.ReceivedOfferMetadata;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.thoughtcrime.securesms.util.NetworkUtil;
import org.thoughtcrime.securesms.util.TelephonyUtil;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.webrtc.PeerConnection;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ACCEPT_CALL;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_BANDWIDTH_MODE_UPDATE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_BLUETOOTH_CHANGE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_CALL_CONCLUDED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_CALL_CONNECTED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_CAMERA_SWITCH_COMPLETED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_CANCEL_PRE_JOIN_CALL;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_DENY_CALL;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_CONNECTION_FAILURE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_INTERNAL_FAILURE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_BUSY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_GLARE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_HANGUP;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_HANGUP_ACCEPTED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_HANGUP_BUSY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_HANGUP_DECLINED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_HANGUP_NEED_PERMISSION;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_SIGNALING_FAILURE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_TIMEOUT;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_FLIP_CAMERA;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_GROUP_APPROVE_SAFETY_CHANGE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_GROUP_CALL_ENDED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_GROUP_CALL_PEEK;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_GROUP_JOINED_MEMBERSHIP_CHANGED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_GROUP_LOCAL_DEVICE_STATE_CHANGED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_GROUP_MESSAGE_SENT_ERROR;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_GROUP_REMOTE_DEVICE_STATE_CHANGED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_GROUP_REQUEST_MEMBERSHIP_PROOF;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_GROUP_REQUEST_UPDATE_MEMBERS;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_GROUP_UPDATE_RENDERED_RESOLUTIONS;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_HTTP_FAILURE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_HTTP_SUCCESS;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_IS_IN_CALL_QUERY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_LOCAL_HANGUP;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_LOCAL_RINGING;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_MESSAGE_SENT_ERROR;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_MESSAGE_SENT_SUCCESS;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_NETWORK_CHANGE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_OUTGOING_CALL;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_PRE_JOIN_CALL;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_RECEIVED_OFFER_EXPIRED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_RECEIVED_OFFER_WHILE_ACTIVE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_RECEIVE_ANSWER;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_RECEIVE_BUSY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_RECEIVE_HANGUP;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_RECEIVE_ICE_CANDIDATES;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_RECEIVE_OFFER;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_RECEIVE_OPAQUE_MESSAGE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_REMOTE_RINGING;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_REMOTE_VIDEO_ENABLE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SCREEN_OFF;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SEND_ANSWER;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SEND_BUSY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SEND_HANGUP;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SEND_ICE_CANDIDATES;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SEND_OFFER;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SEND_OPAQUE_MESSAGE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SETUP_FAILURE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SET_AUDIO_BLUETOOTH;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SET_AUDIO_SPEAKER;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SET_ENABLE_VIDEO;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_SET_MUTE_AUDIO;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_START_INCOMING_CALL;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_START_OUTGOING_CALL;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_TURN_SERVER_UPDATE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_WIRED_HEADSET_CHANGE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_ANSWER_WITH_VIDEO;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_BLUETOOTH;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_IS_ALWAYS_TURN;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_MUTE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_RECIPIENT_IDS;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_RESULT_RECEIVER;
import static org.thoughtcrime.securesms.service.WebRtcCallService.EXTRA_SPEAKER;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcData.AnswerMetadata;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcData.HangupMetadata;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcData.OpaqueMessageMetadata;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcData.ReceivedAnswerMetadata;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getAvailable;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getBroadcastFlag;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getCallId;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getCameraState;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getEnable;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getErrorCallState;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getErrorIdentityKey;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getGroupCallEndReason;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getGroupCallHash;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getGroupMembershipToken;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getIceCandidates;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getIceServers;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getNullableRemotePeerFromMap;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getOfferMessageType;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getRemotePeer;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcIntentParser.getRemotePeerFromMap;

/**
 * Base WebRTC action processor and core of the calling state machine. As actions (as intents)
 * are sent to the service, they are passed to an instance of the current state's action processor.
 * Based on the state of the system, the action processor will either handle the event or do nothing.
 *
 * For example, the {@link OutgoingCallActionProcessor} responds to the the
 * {@link #handleReceivedBusy(WebRtcServiceState, CallMetadata)} event but no others do.
 *
 * Processing of the actions occur in {@link #processAction(String, Intent, WebRtcServiceState)} and
 * result in atomic state updates that are returned to the caller. Part of the state change can be
 * the replacement of the current action processor.
 */
public abstract class WebRtcActionProcessor {

  protected final Context          context;
  protected final WebRtcInteractor webRtcInteractor;
  protected final String           tag;

  public WebRtcActionProcessor(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    this.context          = webRtcInteractor.getWebRtcCallService();
    this.webRtcInteractor = webRtcInteractor;
    this.tag              = tag;
  }

  public @NonNull String getTag() {
    return tag;
  }

  public @NonNull WebRtcServiceState processAction(@NonNull String action, @NonNull Intent intent, @NonNull WebRtcServiceState currentState) {
    switch (action) {
      case ACTION_IS_IN_CALL_QUERY:                    return handleIsInCallQuery(currentState, intent.getParcelableExtra(EXTRA_RESULT_RECEIVER));

      // Pre-Join Actions
      case ACTION_PRE_JOIN_CALL:                       return handlePreJoinCall(currentState, getRemotePeer(intent));
      case ACTION_CANCEL_PRE_JOIN_CALL:                return handleCancelPreJoinCall(currentState);

      // Outgoing Call Actions
      case ACTION_OUTGOING_CALL:                       return handleOutgoingCall(currentState, getRemotePeer(intent), getOfferMessageType(intent));
      case ACTION_START_OUTGOING_CALL:                 return handleStartOutgoingCall(currentState, getRemotePeerFromMap(intent, currentState));
      case ACTION_SEND_OFFER:                          return handleSendOffer(currentState, CallMetadata.fromIntent(intent), OfferMetadata.fromIntent(intent), getBroadcastFlag(intent));
      case ACTION_REMOTE_RINGING:                      return handleRemoteRinging(currentState, getRemotePeerFromMap(intent, currentState));
      case ACTION_RECEIVE_ANSWER:                      return handleReceivedAnswer(currentState, CallMetadata.fromIntent(intent), AnswerMetadata.fromIntent(intent), ReceivedAnswerMetadata.fromIntent(intent));
      case ACTION_RECEIVE_BUSY:                        return handleReceivedBusy(currentState, CallMetadata.fromIntent(intent));

      // Incoming Call Actions
      case ACTION_RECEIVE_OFFER:                       return handleReceivedOffer(currentState, CallMetadata.fromIntent(intent), OfferMetadata.fromIntent(intent), ReceivedOfferMetadata.fromIntent(intent));
      case ACTION_RECEIVED_OFFER_EXPIRED:              return handleReceivedOfferExpired(currentState, getRemotePeerFromMap(intent, currentState));
      case ACTION_START_INCOMING_CALL:                 return handleStartIncomingCall(currentState, getRemotePeerFromMap(intent, currentState));
      case ACTION_ACCEPT_CALL:                         return handleAcceptCall(currentState, intent.getBooleanExtra(EXTRA_ANSWER_WITH_VIDEO, false));
      case ACTION_LOCAL_RINGING:                       return handleLocalRinging(currentState, getRemotePeerFromMap(intent, currentState));
      case ACTION_DENY_CALL:                           return handleDenyCall(currentState);
      case ACTION_SEND_ANSWER:                         return handleSendAnswer(currentState, CallMetadata.fromIntent(intent), AnswerMetadata.fromIntent(intent), getBroadcastFlag(intent));

      // Active Call Actions
      case ACTION_CALL_CONNECTED:                      return handleCallConnected(currentState, getRemotePeerFromMap(intent, currentState));
      case ACTION_RECEIVED_OFFER_WHILE_ACTIVE:         return handleReceivedOfferWhileActive(currentState, getRemotePeerFromMap(intent, currentState));
      case ACTION_SEND_BUSY:                           return handleSendBusy(currentState, CallMetadata.fromIntent(intent), getBroadcastFlag(intent));
      case ACTION_CALL_CONCLUDED:                      return handleCallConcluded(currentState, getNullableRemotePeerFromMap(intent, currentState));
      case ACTION_REMOTE_VIDEO_ENABLE:                 return handleRemoteVideoEnable(currentState, getEnable(intent));
      case ACTION_RECEIVE_HANGUP:                      return handleReceivedHangup(currentState, CallMetadata.fromIntent(intent), HangupMetadata.fromIntent(intent));
      case ACTION_LOCAL_HANGUP:                        return handleLocalHangup(currentState);
      case ACTION_SEND_HANGUP:                         return handleSendHangup(currentState, CallMetadata.fromIntent(intent), HangupMetadata.fromIntent(intent), getBroadcastFlag(intent));
      case ACTION_MESSAGE_SENT_SUCCESS:                return handleMessageSentSuccess(currentState, getCallId(intent));
      case ACTION_MESSAGE_SENT_ERROR:                  return handleMessageSentError(currentState, getCallId(intent), getErrorCallState(intent), getErrorIdentityKey(intent));

      // Call Setup Actions
      case ACTION_RECEIVE_ICE_CANDIDATES:              return handleReceivedIceCandidates(currentState, CallMetadata.fromIntent(intent), getIceCandidates(intent));
      case ACTION_SEND_ICE_CANDIDATES:                 return handleSendIceCandidates(currentState, CallMetadata.fromIntent(intent), getBroadcastFlag(intent), getIceCandidates(intent));
      case ACTION_TURN_SERVER_UPDATE:                  return handleTurnServerUpdate(currentState, getIceServers(intent), intent.getBooleanExtra(EXTRA_IS_ALWAYS_TURN, false));

      // Local Device Actions
      case ACTION_SET_ENABLE_VIDEO:                    return handleSetEnableVideo(currentState, getEnable(intent));
      case ACTION_SET_MUTE_AUDIO:                      return handleSetMuteAudio(currentState, intent.getBooleanExtra(EXTRA_MUTE, false));
      case ACTION_FLIP_CAMERA:                         return handleSetCameraFlip(currentState);
      case ACTION_SCREEN_OFF:                          return handleScreenOffChange(currentState);
      case ACTION_WIRED_HEADSET_CHANGE:                return handleWiredHeadsetChange(currentState, getAvailable(intent));
      case ACTION_SET_AUDIO_SPEAKER:                   return handleSetSpeakerAudio(currentState, intent.getBooleanExtra(EXTRA_SPEAKER, false));
      case ACTION_SET_AUDIO_BLUETOOTH:                 return handleSetBluetoothAudio(currentState, intent.getBooleanExtra(EXTRA_BLUETOOTH, false));
      case ACTION_BLUETOOTH_CHANGE:                    return handleBluetoothChange(currentState, getAvailable(intent));
      case ACTION_CAMERA_SWITCH_COMPLETED:             return handleCameraSwitchCompleted(currentState, getCameraState(intent));
      case ACTION_NETWORK_CHANGE:                      return handleNetworkChanged(currentState, getAvailable(intent));
      case ACTION_BANDWIDTH_MODE_UPDATE:               return handleBandwidthModeUpdate(currentState);

      // End Call Actions
      case ACTION_ENDED_REMOTE_HANGUP:
      case ACTION_ENDED_REMOTE_HANGUP_ACCEPTED:
      case ACTION_ENDED_REMOTE_HANGUP_BUSY:
      case ACTION_ENDED_REMOTE_HANGUP_DECLINED:
      case ACTION_ENDED_REMOTE_BUSY:
      case ACTION_ENDED_REMOTE_HANGUP_NEED_PERMISSION:
      case ACTION_ENDED_REMOTE_GLARE:                  return handleEndedRemote(currentState, action, getRemotePeerFromMap(intent, currentState));

      // End Call Failure Actions
      case ACTION_ENDED_TIMEOUT:
      case ACTION_ENDED_INTERNAL_FAILURE:
      case ACTION_ENDED_SIGNALING_FAILURE:
      case ACTION_ENDED_CONNECTION_FAILURE:            return handleEnded(currentState, action, getRemotePeerFromMap(intent, currentState));

      // Local Call Failure Actions
      case ACTION_SETUP_FAILURE:                       return handleSetupFailure(currentState, getCallId(intent));

      // Group Calling
      case ACTION_GROUP_LOCAL_DEVICE_STATE_CHANGED:    return handleGroupLocalDeviceStateChanged(currentState);
      case ACTION_GROUP_REMOTE_DEVICE_STATE_CHANGED:   return handleGroupRemoteDeviceStateChanged(currentState);
      case ACTION_GROUP_JOINED_MEMBERSHIP_CHANGED:     return handleGroupJoinedMembershipChanged(currentState);
      case ACTION_GROUP_REQUEST_MEMBERSHIP_PROOF:      return handleGroupRequestMembershipProof(currentState, getGroupCallHash(intent), getGroupMembershipToken(intent));
      case ACTION_GROUP_REQUEST_UPDATE_MEMBERS:        return handleGroupRequestUpdateMembers(currentState);
      case ACTION_GROUP_UPDATE_RENDERED_RESOLUTIONS:   return handleUpdateRenderedResolutions(currentState);
      case ACTION_GROUP_CALL_ENDED:                    return handleGroupCallEnded(currentState, getGroupCallHash(intent), getGroupCallEndReason(intent));
      case ACTION_GROUP_CALL_PEEK:                     return handleGroupCallPeek(currentState, getRemotePeer(intent));
      case ACTION_GROUP_MESSAGE_SENT_ERROR:            return handleGroupMessageSentError(currentState, getRemotePeer(intent), getErrorCallState(intent), getErrorIdentityKey(intent));
      case ACTION_GROUP_APPROVE_SAFETY_CHANGE:         return handleGroupApproveSafetyNumberChange(currentState, RecipientId.fromSerializedList(intent.getStringExtra(EXTRA_RECIPIENT_IDS)));

      case ACTION_HTTP_SUCCESS:                        return handleHttpSuccess(currentState, HttpData.fromIntent(intent));
      case ACTION_HTTP_FAILURE:                        return handleHttpFailure(currentState, HttpData.fromIntent(intent));

      case ACTION_SEND_OPAQUE_MESSAGE:                 return handleSendOpaqueMessage(currentState, OpaqueMessageMetadata.fromIntent(intent));
      case ACTION_RECEIVE_OPAQUE_MESSAGE:              return handleReceivedOpaqueMessage(currentState, OpaqueMessageMetadata.fromIntent(intent));
    }

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleIsInCallQuery(@NonNull WebRtcServiceState currentState, @Nullable ResultReceiver resultReceiver) {
    if (resultReceiver != null) {
      resultReceiver.send(0, null);
    }
    return currentState;
  }

  //region Pre-Join

  protected @NonNull WebRtcServiceState handlePreJoinCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handlePreJoinCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleCancelPreJoinCall(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleCancelPreJoinCall not processed");
    return currentState;
  }

  //endregion Pre-Join

  //region Outgoing Call

  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer, @NonNull OfferMessage.Type offerType) {
    Log.i(tag, "handleOutgoingCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleStartOutgoingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleStartOutgoingCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSendOffer(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata, @NonNull OfferMetadata offerMetadata, boolean broadcast) {
    Log.i(tag, "handleSendOffer not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleRemoteRinging(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleRemoteRinging not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedAnswer(@NonNull WebRtcServiceState currentState,
                                                             @NonNull CallMetadata callMetadata,
                                                             @NonNull AnswerMetadata answerMetadata,
                                                             @NonNull ReceivedAnswerMetadata receivedAnswerMetadata)
  {
    Log.i(tag, "handleReceivedAnswer not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedBusy(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata) {
    Log.i(tag, "handleReceivedBusy not processed");
    return currentState;
  }

  //endregion Outgoing call

  //region Incoming call

  protected @NonNull WebRtcServiceState handleReceivedOffer(@NonNull WebRtcServiceState currentState,
                                                            @NonNull CallMetadata callMetadata,
                                                            @NonNull OfferMetadata offerMetadata,
                                                            @NonNull ReceivedOfferMetadata receivedOfferMetadata)
  {
    Log.i(tag, "handleReceivedOffer(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    if (TelephonyUtil.isAnyPstnLineBusy(context)) {
      Log.i(tag, "PSTN line is busy.");
      currentState = currentState.getActionProcessor().handleSendBusy(currentState, callMetadata, true);
      webRtcInteractor.insertMissedCall(callMetadata.getRemotePeer(), true, receivedOfferMetadata.getServerReceivedTimestamp(), offerMetadata.getOfferType() == OfferMessage.Type.VIDEO_CALL);
      return currentState;
    }

    if (!RecipientUtil.isCallRequestAccepted(context.getApplicationContext(), callMetadata.getRemotePeer().getRecipient())) {
      Log.w(tag, "Caller is untrusted.");
      currentState = currentState.getActionProcessor().handleSendHangup(currentState, callMetadata, WebRtcData.HangupMetadata.fromType(HangupMessage.Type.NEED_PERMISSION), true);
      webRtcInteractor.insertMissedCall(callMetadata.getRemotePeer(), true, receivedOfferMetadata.getServerReceivedTimestamp(), offerMetadata.getOfferType() == OfferMessage.Type.VIDEO_CALL);
      return currentState;
    }

    Log.i(tag, "add remotePeer callId: " + callMetadata.getRemotePeer().getCallId() + " key: " + callMetadata.getRemotePeer().hashCode());

    callMetadata.getRemotePeer().setCallStartTimestamp(receivedOfferMetadata.getServerReceivedTimestamp());

    currentState = currentState.builder()
                               .changeCallSetupState()
                               .isRemoteVideoOffer(offerMetadata.getOfferType() == OfferMessage.Type.VIDEO_CALL)
                               .commit()
                               .changeCallInfoState()
                               .putRemotePeer(callMetadata.getRemotePeer())
                               .build();

    long messageAgeSec = Math.max(receivedOfferMetadata.getServerDeliveredTimestamp() - receivedOfferMetadata.getServerReceivedTimestamp(), 0) / 1000;
    Log.i(tag, "messageAgeSec: " + messageAgeSec + ", serverReceivedTimestamp: " + receivedOfferMetadata.getServerReceivedTimestamp() + ", serverDeliveredTimestamp: " + receivedOfferMetadata.getServerDeliveredTimestamp());

    try {
      byte[] remoteIdentityKey = WebRtcUtil.getPublicKeyBytes(receivedOfferMetadata.getRemoteIdentityKey());
      byte[] localIdentityKey  = WebRtcUtil.getPublicKeyBytes(IdentityKeyUtil.getIdentityKey(context).serialize());

      webRtcInteractor.getCallManager().receivedOffer(callMetadata.getCallId(),
                                                      callMetadata.getRemotePeer(),
                                                      callMetadata.getRemoteDevice(),
                                                      offerMetadata.getOpaque(),
                                                      offerMetadata.getSdp(),
                                                      messageAgeSec,
                                                      WebRtcUtil.getCallMediaTypeFromOfferType(offerMetadata.getOfferType()),
                                                      1,
                                                      receivedOfferMetadata.isMultiRing(),
                                                      true,
                                                      remoteIdentityKey,
                                                      localIdentityKey);
    } catch (CallException | InvalidKeyException e) {
      return callFailure(currentState, "Unable to process received offer: ", e);
    }

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedOfferExpired(@NonNull WebRtcServiceState currentState,
                                                                   @NonNull RemotePeer remotePeer)
  {
    Log.i(tag, "handleReceivedOfferExpired(): call_id: " + remotePeer.getCallId());

    webRtcInteractor.insertMissedCall(remotePeer, true, remotePeer.getCallStartTimestamp(), currentState.getCallSetupState().isRemoteVideoOffer());

    return terminate(currentState, remotePeer);
  }

  protected @NonNull WebRtcServiceState handleStartIncomingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleStartIncomingCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleAcceptCall(@NonNull WebRtcServiceState currentState, boolean answerWithVideo) {
    Log.i(tag, "handleAcceptCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleLocalRinging(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleLocalRinging not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleDenyCall(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleDenyCall not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSendAnswer(@NonNull WebRtcServiceState currentState,
                                                         @NonNull CallMetadata callMetadata,
                                                         @NonNull AnswerMetadata answerMetadata,
                                                         boolean broadcast)
  {
    Log.i(tag, "handleSendAnswer not processed");
    return currentState;
  }

  //endregion Incoming call

  //region Active call

  protected @NonNull WebRtcServiceState handleCallConnected(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleCallConnected not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedOfferWhileActive(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleReceivedOfferWhileActive not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSendBusy(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata, boolean broadcast) {
    Log.i(tag, "handleSendBusy(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    BusyMessage              busyMessage         = new BusyMessage(callMetadata.getCallId().longValue());
    Integer                  destinationDeviceId = broadcast ? null : callMetadata.getRemoteDevice();
    SignalServiceCallMessage callMessage         = SignalServiceCallMessage.forBusy(busyMessage, true, destinationDeviceId);

    webRtcInteractor.sendCallMessage(callMetadata.getRemotePeer(), callMessage);

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleCallConcluded(@NonNull WebRtcServiceState currentState, @Nullable RemotePeer remotePeer) {
    Log.i(tag, "handleCallConcluded not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleRemoteVideoEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(tag, "handleRemoteVideoEnable not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedHangup(@NonNull WebRtcServiceState currentState,
                                                             @NonNull CallMetadata callMetadata,
                                                             @NonNull HangupMetadata hangupMetadata)
  {
    Log.i(tag, "handleReceivedHangup(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    try {
      webRtcInteractor.getCallManager().receivedHangup(callMetadata.getCallId(), callMetadata.getRemoteDevice(), hangupMetadata.getCallHangupType(), hangupMetadata.getDeviceId());
    } catch  (CallException e) {
      return callFailure(currentState, "receivedHangup() failed: ", e);
    }

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleLocalHangup not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSendHangup(@NonNull WebRtcServiceState currentState,
                                                         @NonNull CallMetadata callMetadata,
                                                         @NonNull HangupMetadata hangupMetadata,
                                                         boolean broadcast)
  {
    Log.i(tag, "handleSendHangup(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    HangupMessage            hangupMessage       = new HangupMessage(callMetadata.getCallId().longValue(), hangupMetadata.getType(), hangupMetadata.getDeviceId(), hangupMetadata.isLegacy());
    Integer                  destinationDeviceId = broadcast ? null : callMetadata.getRemoteDevice();
    SignalServiceCallMessage callMessage         = SignalServiceCallMessage.forHangup(hangupMessage, true, destinationDeviceId);

    webRtcInteractor.sendCallMessage(callMetadata.getRemotePeer(), callMessage);

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleMessageSentSuccess(@NonNull WebRtcServiceState currentState, @NonNull CallId callId) {
    try {
      webRtcInteractor.getCallManager().messageSent(callId);
    } catch (CallException e) {
      return callFailure(currentState, "callManager.messageSent() failed: ", e);
    }
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleMessageSentError(@NonNull WebRtcServiceState currentState,
                                                               @NonNull CallId callId,
                                                               @NonNull WebRtcViewModel.State errorCallState,
                                                               @NonNull Optional<IdentityKey> identityKey) {
    Log.w(tag, "handleMessageSentError():");

    try {
      webRtcInteractor.getCallManager().messageSendFailure(callId);
    } catch (CallException e) {
      currentState = callFailure(currentState, "callManager.messageSendFailure() failed: ", e);
    }

    RemotePeer activePeer = currentState.getCallInfoState().getActivePeer();
    if (activePeer == null) {
      return currentState;
    }

    WebRtcServiceStateBuilder builder = currentState.builder();

    if (errorCallState == WebRtcViewModel.State.UNTRUSTED_IDENTITY) {
      CallParticipant participant = Objects.requireNonNull(currentState.getCallInfoState().getRemoteCallParticipant(activePeer.getRecipient()));
      CallParticipant untrusted   = participant.withIdentityKey(identityKey.get());

      builder.changeCallInfoState()
             .callState(WebRtcViewModel.State.UNTRUSTED_IDENTITY)
             .putParticipant(activePeer.getRecipient(), untrusted)
             .commit();
    } else {
      builder.changeCallInfoState()
             .callState(errorCallState)
             .commit();
    }

    return builder.build();
  }

  //endregion Active call

  //region Call setup

  protected @NonNull WebRtcServiceState handleSendIceCandidates(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata, boolean broadcast, @NonNull ArrayList<IceCandidateParcel> iceCandidates) {
    Log.i(tag, "handleSendIceCandidates not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedIceCandidates(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata, @NonNull ArrayList<IceCandidateParcel> iceCandidateParcels) {
    Log.i(tag, "handleReceivedIceCandidates not processed");
    return currentState;
  }

  public @NonNull WebRtcServiceState handleTurnServerUpdate(@NonNull WebRtcServiceState currentState, @NonNull List<PeerConnection.IceServer> iceServers, boolean isAlwaysTurn) {
    Log.i(tag, "handleTurnServerUpdate not processed");
    return currentState;
  }

  //endregion Call setup

  //region Local device

  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(tag, "handleSetEnableVideo not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSetMuteAudio(@NonNull WebRtcServiceState currentState, boolean muted) {
    Log.i(tag, "handleSetMuteAudio not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSetSpeakerAudio(@NonNull WebRtcServiceState currentState, boolean isSpeaker) {
    Log.i(tag, "handleSetSpeakerAudio not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSetBluetoothAudio(@NonNull WebRtcServiceState currentState, boolean isBluetooth) {
    Log.i(tag, "handleSetBluetoothAudio not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSetCameraFlip(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleSetCameraFlip not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleScreenOffChange(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleScreenOffChange not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleBluetoothChange(@NonNull WebRtcServiceState currentState, boolean available) {
    Log.i(tag, "handleBluetoothChange not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleWiredHeadsetChange(@NonNull WebRtcServiceState currentState, boolean present) {
    Log.i(tag, "handleWiredHeadsetChange not processed");
    return currentState;
  }

  public @NonNull WebRtcServiceState handleCameraSwitchCompleted(@NonNull WebRtcServiceState currentState, @NonNull CameraState newCameraState) {
    Log.i(tag, "handleCameraSwitchCompleted not processed");
    return currentState;
  }

  public @NonNull WebRtcServiceState handleNetworkChanged(@NonNull WebRtcServiceState currentState, boolean available) {
    Log.i(tag, "handleNetworkChanged not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleBandwidthModeUpdate(@NonNull WebRtcServiceState currentState) {
    try {
      webRtcInteractor.getCallManager().setLowBandwidthMode(NetworkUtil.useLowBandwidthCalling(context));
    } catch (CallException e) {
      Log.i(tag, "handleBandwidthModeUpdate: could not update bandwidth mode.");
    }

    return currentState;
  }

  //endregion Local device

  //region End call

  protected @NonNull WebRtcServiceState handleEndedRemote(@NonNull WebRtcServiceState currentState, @NonNull String action, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleEndedRemote not processed");
    return currentState;
  }

  //endregion End call

  //region End call failure

  protected @NonNull WebRtcServiceState handleEnded(@NonNull WebRtcServiceState currentState, @NonNull String action, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleEnded not processed");
    return currentState;
  }

  //endregion

  //region Local call failure

  protected  @NonNull WebRtcServiceState handleSetupFailure(@NonNull WebRtcServiceState currentState, @NonNull CallId callId) {
    Log.i(tag, "handleSetupFailure not processed");
    return currentState;
  }

  //endregion

  //region Global call operations

  public @NonNull WebRtcServiceState callFailure(@NonNull WebRtcServiceState currentState,
                                                 @Nullable String message,
                                                 @Nullable Throwable error)
  {
    Log.w(tag, "callFailure(): " + message, error);

    WebRtcServiceStateBuilder builder = currentState.builder();

    if (currentState.getCallInfoState().getActivePeer() != null) {
      builder.changeCallInfoState()
             .callState(WebRtcViewModel.State.CALL_DISCONNECTED);
    }

    try {
      webRtcInteractor.getCallManager().reset();
    } catch (CallException e) {
      Log.w(tag, "Unable to reset call manager: ", e);
    }

    currentState = builder.changeCallInfoState().clearPeerMap().build();
    return terminate(currentState, currentState.getCallInfoState().getActivePeer());
  }

  public synchronized @NonNull WebRtcServiceState terminate(@NonNull WebRtcServiceState currentState, @Nullable RemotePeer remotePeer) {
    Log.i(tag, "terminate():");

    RemotePeer activePeer = currentState.getCallInfoState().getActivePeer();

    if (activePeer == null) {
      Log.i(tag, "skipping with no active peer");
      return currentState;
    }

    if (!activePeer.callIdEquals(remotePeer)) {
      Log.i(tag, "skipping remotePeer is not active peer");
      return currentState;
    }

    webRtcInteractor.updatePhoneState(LockManager.PhoneState.PROCESSING);
    webRtcInteractor.stopForegroundService();
    boolean playDisconnectSound = (activePeer.getState() == CallState.DIALING) ||
                                  (activePeer.getState() == CallState.REMOTE_RINGING) ||
                                  (activePeer.getState() == CallState.RECEIVED_BUSY) ||
                                  (activePeer.getState() == CallState.CONNECTED);
    webRtcInteractor.stopAudio(playDisconnectSound);
    webRtcInteractor.setWantsBluetoothConnection(false);

    webRtcInteractor.updatePhoneState(LockManager.PhoneState.IDLE);

    return WebRtcVideoUtil.deinitializeVideo(currentState)
                          .builder()
                          .changeCallInfoState()
                          .activePeer(null)
                          .commit()
                          .actionProcessor(currentState.getCallInfoState().getCallState() == WebRtcViewModel.State.CALL_DISCONNECTED ? new DisconnectingCallActionProcessor(webRtcInteractor) : new IdleActionProcessor(webRtcInteractor))
                          .terminate()
                          .build();
  }

  //endregion

  //region Group Calling

  protected @NonNull WebRtcServiceState handleGroupLocalDeviceStateChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupLocalDeviceStateChanged not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleGroupRemoteDeviceStateChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupRemoteDeviceStateChanged not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleGroupJoinedMembershipChanged(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupJoinedMembershipChanged not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleGroupRequestMembershipProof(@NonNull WebRtcServiceState currentState, int groupCallHash, @NonNull byte[] groupMembershipToken) {
    Log.i(tag, "handleGroupRequestMembershipProof not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleGroupRequestUpdateMembers(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleGroupRequestUpdateMembers not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleUpdateRenderedResolutions(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleUpdateRenderedResolutions not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleGroupCallEnded(@NonNull WebRtcServiceState currentState, int groupCallHash, @NonNull GroupCall.GroupCallEndReason groupCallEndReason) {
    Log.i(tag, "handleGroupCallEnded not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleGroupCallPeek(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    webRtcInteractor.peekGroupCall(remotePeer.getId());
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleGroupMessageSentError(@NonNull WebRtcServiceState currentState,
                                                                    @NonNull RemotePeer remotePeer,
                                                                    @NonNull WebRtcViewModel.State errorCallState,
                                                                    @NonNull Optional<IdentityKey> identityKey)
  {
    Log.i(tag, "handleGroupMessageSentError not processed");
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleGroupApproveSafetyNumberChange(@NonNull WebRtcServiceState currentState,
                                                                             @NonNull List<RecipientId> recipientIds)
  {
    Log.i(tag, "handleGroupApproveSafetyNumberChange not processed");
    return currentState;
  }

  //endregion

  protected @NonNull WebRtcServiceState handleHttpSuccess(@NonNull WebRtcServiceState currentState, @NonNull HttpData httpData) {
    try {
      webRtcInteractor.getCallManager().receivedHttpResponse(httpData.getRequestId(), httpData.getStatus(), httpData.getBody() != null ? httpData.getBody() : new byte[0]);
    } catch (CallException e) {
      return callFailure(currentState, "Unable to process received http response", e);
    }
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleHttpFailure(@NonNull WebRtcServiceState currentState, @NonNull HttpData httpData) {
    try {
      webRtcInteractor.getCallManager().httpRequestFailed(httpData.getRequestId());
    } catch (CallException e) {
      return callFailure(currentState, "Unable to process received http response", e);
    }
    return currentState;
  }

  protected @NonNull WebRtcServiceState handleSendOpaqueMessage(@NonNull WebRtcServiceState currentState, @NonNull OpaqueMessageMetadata opaqueMessageMetadata) {
    Log.i(tag, "handleSendOpaqueMessage not processed");

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleReceivedOpaqueMessage(@NonNull WebRtcServiceState currentState, @NonNull OpaqueMessageMetadata opaqueMessageMetadata) {
    Log.i(tag, "handleReceivedOpaqueMessage not processed");

    return currentState;
  }
}
