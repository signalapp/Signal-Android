package org.thoughtcrime.securesms.service.webrtc;

import android.os.ResultReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.IceCandidate;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.ringrtc.CallState;
import org.thoughtcrime.securesms.ringrtc.IceCandidateParcel;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.webrtc.audio.OutgoingRinger;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_BUSY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_GLARE;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_HANGUP;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_HANGUP_ACCEPTED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_HANGUP_BUSY;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_HANGUP_DECLINED;
import static org.thoughtcrime.securesms.service.WebRtcCallService.ACTION_ENDED_REMOTE_HANGUP_NEED_PERMISSION;
import static org.thoughtcrime.securesms.service.WebRtcCallService.BUSY_TONE_LENGTH;
import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_ESTABLISHED;
import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_CONNECTING;
import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_RINGING;
import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_OUTGOING_RINGING;

/**
 * Encapsulates the shared logic to manage an active 1:1 call. An active call is any call that is being setup
 * or ongoing. Other action processors delegate the appropriate action to it but it is not intended
 * to be the main processor for the system.
 */
public class ActiveCallActionProcessorDelegate extends WebRtcActionProcessor {

  private static final Map<String, WebRtcViewModel.State> ENDED_ACTION_TO_STATE = new HashMap<String, WebRtcViewModel.State>() {{
    put(ACTION_ENDED_REMOTE_HANGUP_ACCEPTED, WebRtcViewModel.State.CALL_ACCEPTED_ELSEWHERE);
    put(ACTION_ENDED_REMOTE_HANGUP_BUSY, WebRtcViewModel.State.CALL_ONGOING_ELSEWHERE);
    put(ACTION_ENDED_REMOTE_HANGUP_DECLINED, WebRtcViewModel.State.CALL_DECLINED_ELSEWHERE);
    put(ACTION_ENDED_REMOTE_BUSY, WebRtcViewModel.State.CALL_BUSY);
    put(ACTION_ENDED_REMOTE_HANGUP_NEED_PERMISSION, WebRtcViewModel.State.CALL_NEEDS_PERMISSION);
    put(ACTION_ENDED_REMOTE_GLARE, WebRtcViewModel.State.CALL_DISCONNECTED);
  }};

  public ActiveCallActionProcessorDelegate(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    super(webRtcInteractor, tag);
  }

  @Override
  protected @NonNull WebRtcServiceState handleIsInCallQuery(@NonNull WebRtcServiceState currentState, @Nullable ResultReceiver resultReceiver) {
    if (resultReceiver != null) {
      resultReceiver.send(1, null);
    }
    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleSendIceCandidates(@NonNull WebRtcServiceState currentState,
                                                                @NonNull WebRtcData.CallMetadata callMetadata,
                                                                boolean broadcast,
                                                                @NonNull ArrayList<IceCandidateParcel> iceCandidates)
  {
    Log.i(tag, "handleSendIceCandidates(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    LinkedList<IceUpdateMessage> iceUpdateMessages = new LinkedList<>();
    for (IceCandidateParcel parcel : iceCandidates) {
      iceUpdateMessages.add(parcel.getIceUpdateMessage(callMetadata.getCallId()));
    }

    Integer                  destinationDeviceId = broadcast ? null : callMetadata.getRemoteDevice();
    SignalServiceCallMessage callMessage         = SignalServiceCallMessage.forIceUpdates(iceUpdateMessages, true, destinationDeviceId);

    webRtcInteractor.sendCallMessage(callMetadata.getRemotePeer(), callMessage);

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedIceCandidates(@NonNull WebRtcServiceState currentState,
                                                                    @NonNull WebRtcData.CallMetadata callMetadata,
                                                                    @NonNull ArrayList<IceCandidateParcel> iceCandidateParcels)
  {
    Log.i(tag, "handleReceivedIceCandidates(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()) + ", count: " + iceCandidateParcels.size());

    LinkedList<IceCandidate> iceCandidates = new LinkedList<>();
    for (IceCandidateParcel parcel : iceCandidateParcels) {
      iceCandidates.add(parcel.getIceCandidate());
    }

    try {
      webRtcInteractor.getCallManager().receivedIceCandidates(callMetadata.getCallId(), callMetadata.getRemoteDevice(), iceCandidates);
    } catch (CallException e) {
      return callFailure(currentState, "receivedIceCandidates() failed: ", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull  WebRtcServiceState handleRemoteVideoEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    RemotePeer activePeer = currentState.getCallInfoState().requireActivePeer();

    Log.i(tag, "handleRemoteVideoEnable(): call_id: " + activePeer.getCallId());

    CallParticipant oldParticipant = Objects.requireNonNull(currentState.getCallInfoState().getRemoteParticipant(activePeer.getRecipient()));
    CallParticipant newParticipant = oldParticipant.withVideoEnabled(enable);

    return currentState.builder()
                       .changeCallInfoState()
                       .putParticipant(activePeer.getRecipient(), newParticipant)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleLocalHangup(): call_id: " + currentState.getCallInfoState().requireActivePeer().getCallId());

    ApplicationDependencies.getSignalServiceAccountManager().cancelInFlightRequests();
    ApplicationDependencies.getSignalServiceMessageSender().cancelInFlightRequests();

    try {
      webRtcInteractor.getCallManager().hangup();

      currentState = currentState.builder()
                                 .changeCallInfoState()
                                 .callState(WebRtcViewModel.State.CALL_DISCONNECTED)
                                 .build();

      webRtcInteractor.sendMessage(currentState);

      return terminate(currentState, currentState.getCallInfoState().getActivePeer());
    } catch (CallException e) {
      return callFailure(currentState, "hangup() failed: ", e);
    }
  }

  @Override
  protected @NonNull WebRtcServiceState handleCallConcluded(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleCallConcluded():");
    Log.i(tag, "delete remotePeer callId: " + remotePeer.getCallId() + " key: " + remotePeer.hashCode());
    return currentState.builder()
                       .changeCallInfoState()
                       .removeRemotePeer(remotePeer)
                       .build();
  }

  @Override
  protected  @NonNull WebRtcServiceState handleReceivedOfferWhileActive(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    RemotePeer activePeer = currentState.getCallInfoState().requireActivePeer();

    Log.i(tag, "handleReceivedOfferWhileActive(): call_id: " + remotePeer.getCallId());

    switch (activePeer.getState()) {
      case DIALING:
      case REMOTE_RINGING: webRtcInteractor.setCallInProgressNotification(TYPE_OUTGOING_RINGING,    activePeer); break;
      case IDLE:
      case ANSWERING:      webRtcInteractor.setCallInProgressNotification(TYPE_INCOMING_CONNECTING, activePeer); break;
      case LOCAL_RINGING:  webRtcInteractor.setCallInProgressNotification(TYPE_INCOMING_RINGING,    activePeer); break;
      case CONNECTED:      webRtcInteractor.setCallInProgressNotification(TYPE_ESTABLISHED,         activePeer); break;
      default:             throw new IllegalStateException();
    }

    if (activePeer.getState() == CallState.IDLE) {
      webRtcInteractor.stopForegroundService();
    }

    webRtcInteractor.insertMissedCall(remotePeer, true, remotePeer.getCallStartTimestamp(), currentState.getCallSetupState().isRemoteVideoOffer());

    return terminate(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEndedRemote(@NonNull WebRtcServiceState currentState,
                                                          @NonNull String action,
                                                          @NonNull RemotePeer remotePeer)
  {
    Log.i(tag, "handleEndedRemote(): call_id: " + remotePeer.getCallId() + " action: " + action);

    WebRtcViewModel.State state                = currentState.getCallInfoState().getCallState();
    RemotePeer            activePeer           = currentState.getCallInfoState().getActivePeer();
    boolean               remotePeerIsActive   = remotePeer.callIdEquals(activePeer);
    boolean               outgoingBeforeAccept = remotePeer.getState() == CallState.DIALING || remotePeer.getState() == CallState.REMOTE_RINGING;
    boolean               incomingBeforeAccept = remotePeer.getState() == CallState.ANSWERING || remotePeer.getState() == CallState.LOCAL_RINGING;

    if (remotePeerIsActive && ENDED_ACTION_TO_STATE.containsKey(action)) {
      state = Objects.requireNonNull(ENDED_ACTION_TO_STATE.get(action));
    }

    if (action.equals(ACTION_ENDED_REMOTE_HANGUP)) {
      if (remotePeerIsActive) {
        state = outgoingBeforeAccept ? WebRtcViewModel.State.RECIPIENT_UNAVAILABLE : WebRtcViewModel.State.CALL_DISCONNECTED;
      }

      if (incomingBeforeAccept) {
        webRtcInteractor.insertMissedCall(remotePeer, true, remotePeer.getCallStartTimestamp(), currentState.getCallSetupState().isRemoteVideoOffer());
      }
    } else if (action.equals(ACTION_ENDED_REMOTE_BUSY) && remotePeerIsActive) {
      activePeer.receivedBusy();

      OutgoingRinger ringer = new OutgoingRinger(context);
      ringer.start(OutgoingRinger.Type.BUSY);
      Util.runOnMainDelayed(ringer::stop, BUSY_TONE_LENGTH);
    } else if (action.equals(ACTION_ENDED_REMOTE_GLARE) && incomingBeforeAccept) {
      webRtcInteractor.insertMissedCall(remotePeer, true, remotePeer.getCallStartTimestamp(), currentState.getCallSetupState().isRemoteVideoOffer());
    }

    currentState = currentState.builder()
                               .changeCallInfoState()
                               .callState(state)
                               .build();

    webRtcInteractor.sendMessage(currentState);

    return terminate(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEnded(@NonNull WebRtcServiceState currentState, @NonNull String action, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleEnded(): call_id: " + remotePeer.getCallId() + " action: " + action);

    if (remotePeer.callIdEquals(currentState.getCallInfoState().getActivePeer())) {
      currentState = currentState.builder()
                                 .changeCallInfoState()
                                 .callState(WebRtcViewModel.State.NETWORK_FAILURE)
                                 .build();

      webRtcInteractor.sendMessage(currentState);
    }

    if (remotePeer.getState() == CallState.ANSWERING || remotePeer.getState() == CallState.LOCAL_RINGING) {
      webRtcInteractor.insertMissedCall(remotePeer, true, remotePeer.getCallStartTimestamp(), currentState.getCallSetupState().isRemoteVideoOffer());
    }

    return terminate(currentState, remotePeer);
  }

  @Override
  protected  @NonNull WebRtcServiceState handleSetupFailure(@NonNull WebRtcServiceState currentState, @NonNull CallId callId) {
    Log.i(tag, "handleSetupFailure(): call_id: " + callId);

    RemotePeer activePeer = currentState.getCallInfoState().getActivePeer();

    if (activePeer != null && activePeer.getCallId().equals(callId)) {
      try {
        if (activePeer.getState() == CallState.DIALING || activePeer.getState() == CallState.REMOTE_RINGING) {
          webRtcInteractor.getCallManager().hangup();
        } else {
          webRtcInteractor.getCallManager().drop(callId);
        }
      } catch (CallException e) {
        return callFailure(currentState, "Unable to drop call due to setup failure", e);
      }

      currentState = currentState.builder()
                                 .changeCallInfoState()
                                 .callState(WebRtcViewModel.State.NETWORK_FAILURE)
                                 .build();

      webRtcInteractor.sendMessage(currentState);

      if (activePeer.getState() == CallState.ANSWERING || activePeer.getState() == CallState.LOCAL_RINGING) {
        webRtcInteractor.insertMissedCall(activePeer, true, activePeer.getCallStartTimestamp(), currentState.getCallSetupState().isRemoteVideoOffer());
      }

      return terminate(currentState, activePeer);
    }

    return currentState;
  }
}
