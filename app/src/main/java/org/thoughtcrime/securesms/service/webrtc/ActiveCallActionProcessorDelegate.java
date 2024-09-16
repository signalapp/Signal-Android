package org.thoughtcrime.securesms.service.webrtc;

import android.os.ResultReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager.CallEvent;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.ringrtc.CallState;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.webrtc.audio.OutgoingRinger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

  private static final Map<CallEvent, WebRtcViewModel.State> ENDED_REMOTE_EVENT_TO_STATE = new HashMap<CallEvent, WebRtcViewModel.State>() {{
    put(CallEvent.ENDED_REMOTE_HANGUP_ACCEPTED, WebRtcViewModel.State.CALL_ACCEPTED_ELSEWHERE);
    put(CallEvent.ENDED_REMOTE_HANGUP_BUSY, WebRtcViewModel.State.CALL_ONGOING_ELSEWHERE);
    put(CallEvent.ENDED_REMOTE_HANGUP_DECLINED, WebRtcViewModel.State.CALL_DECLINED_ELSEWHERE);
    put(CallEvent.ENDED_REMOTE_BUSY, WebRtcViewModel.State.CALL_BUSY);
    put(CallEvent.ENDED_REMOTE_HANGUP_NEED_PERMISSION, WebRtcViewModel.State.CALL_NEEDS_PERMISSION);
    put(CallEvent.ENDED_REMOTE_GLARE, WebRtcViewModel.State.CALL_DISCONNECTED_GLARE);
    put(CallEvent.ENDED_REMOTE_RECALL, WebRtcViewModel.State.CALL_DISCONNECTED_GLARE);
  }};

  public ActiveCallActionProcessorDelegate(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    super(webRtcInteractor, tag);
  }

  @Override
  protected @NonNull WebRtcServiceState handleIsInCallQuery(@NonNull WebRtcServiceState currentState, @Nullable ResultReceiver resultReceiver) {
    if (resultReceiver != null) {
      resultReceiver.send(1, ActiveCallData.fromCallState(currentState).toBundle());
    }
    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleRemoteVideoEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    RemotePeer activePeer = currentState.getCallInfoState().requireActivePeer();

    Log.i(tag, "handleRemoteVideoEnable(): call_id: " + activePeer.getCallId());

    CallParticipant oldParticipant = Objects.requireNonNull(currentState.getCallInfoState().getRemoteCallParticipant(activePeer.getRecipient()));
    CallParticipant newParticipant = oldParticipant.withVideoEnabled(enable);

    return currentState.builder()
                       .changeCallInfoState()
                       .putParticipant(activePeer.getRecipient(), newParticipant)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleScreenSharingEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    RemotePeer activePeer = currentState.getCallInfoState().requireActivePeer();

    Log.i(tag, "handleScreenSharingEnable(): call_id: " + activePeer.getCallId() + " enable: " + enable);

    CallParticipant oldParticipant = Objects.requireNonNull(currentState.getCallInfoState().getRemoteCallParticipant(activePeer.getRecipient()));
    CallParticipant newParticipant = oldParticipant.withScreenSharingEnabled(enable);

    return currentState.builder()
                       .changeCallInfoState()
                       .putParticipant(activePeer.getRecipient(), newParticipant)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    RemotePeer remotePeer = currentState.getCallInfoState().getActivePeer();
    if (remotePeer == null) {
      Log.i(tag, "handleLocalHangup(): no active peer");
    } else {
      Log.i(tag, "handleLocalHangup(): call_id: " + remotePeer.getCallId());
    }

    AppDependencies.getSignalServiceAccountManager().cancelInFlightRequests();
    AppDependencies.getSignalServiceMessageSender().cancelInFlightRequests();

    try {
      webRtcInteractor.getCallManager().hangup();

      currentState = currentState.builder()
                                 .changeCallInfoState()
                                 .callState(WebRtcViewModel.State.CALL_DISCONNECTED)
                                 .build();

      webRtcInteractor.postStateUpdate(currentState);

      return terminate(currentState, remotePeer);
    } catch (CallException e) {
      return callFailure(currentState, "hangup() failed: ", e);
    }
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedOfferWhileActive(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    RemotePeer activePeer         = currentState.getCallInfoState().requireActivePeer();
    boolean    isRemoteVideoOffer = currentState.getCallSetupState(activePeer).isRemoteVideoOffer();

    Log.i(tag, "handleReceivedOfferWhileActive(): call_id: " + remotePeer.getCallId());

    switch (activePeer.getState()) {
      case DIALING:
      case REMOTE_RINGING:
        webRtcInteractor.setCallInProgressNotification(TYPE_OUTGOING_RINGING, activePeer, isRemoteVideoOffer);
        break;
      case IDLE:
      case ANSWERING:
        webRtcInteractor.setCallInProgressNotification(TYPE_INCOMING_CONNECTING, activePeer, isRemoteVideoOffer);
        break;
      case LOCAL_RINGING:
        webRtcInteractor.setCallInProgressNotification(TYPE_INCOMING_RINGING, activePeer, isRemoteVideoOffer);
        break;
      case CONNECTED:
        webRtcInteractor.setCallInProgressNotification(TYPE_ESTABLISHED, activePeer, isRemoteVideoOffer);
        break;
      default:
        throw new IllegalStateException();
    }

    if (activePeer.getState() == CallState.IDLE) {
      webRtcInteractor.stopForegroundService();
    }

    webRtcInteractor.insertMissedCall(remotePeer, remotePeer.getCallStartTimestamp(), currentState.getCallSetupState(remotePeer).isRemoteVideoOffer());

    return terminate(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEndedRemote(@NonNull WebRtcServiceState currentState,
                                                          @NonNull CallEvent endedRemoteEvent,
                                                          @NonNull RemotePeer remotePeer)
  {
    Log.i(tag, "handleEndedRemote(): call_id: " + remotePeer.getCallId() + " action: " + endedRemoteEvent);

    WebRtcViewModel.State state                = currentState.getCallInfoState().getCallState();
    RemotePeer            activePeer           = currentState.getCallInfoState().getActivePeer();
    boolean               remotePeerIsActive   = remotePeer.callIdEquals(activePeer);
    boolean               outgoingBeforeAccept = remotePeer.getState() == CallState.DIALING || remotePeer.getState() == CallState.REMOTE_RINGING;
    boolean               incomingBeforeAccept = remotePeer.getState() == CallState.ANSWERING || remotePeer.getState() == CallState.LOCAL_RINGING;

    if (remotePeerIsActive && ENDED_REMOTE_EVENT_TO_STATE.containsKey(endedRemoteEvent)) {
      state = Objects.requireNonNull(ENDED_REMOTE_EVENT_TO_STATE.get(endedRemoteEvent));
    }

    if (endedRemoteEvent == CallEvent.ENDED_REMOTE_HANGUP || endedRemoteEvent == CallEvent.ENDED_REMOTE_HANGUP_BUSY) {
      if (remotePeerIsActive) {
        state = outgoingBeforeAccept ? WebRtcViewModel.State.RECIPIENT_UNAVAILABLE : WebRtcViewModel.State.CALL_DISCONNECTED;
      }

      if (incomingBeforeAccept) {
        webRtcInteractor.insertMissedCall(remotePeer, remotePeer.getCallStartTimestamp(), currentState.getCallSetupState(remotePeer).isRemoteVideoOffer());
      }
    } else if (endedRemoteEvent == CallEvent.ENDED_REMOTE_BUSY && remotePeerIsActive) {
      activePeer.receivedBusy();

      OutgoingRinger ringer = new OutgoingRinger(context);
      ringer.start(OutgoingRinger.Type.BUSY);
      ThreadUtil.runOnMainDelayed(ringer::stop, SignalCallManager.BUSY_TONE_LENGTH);
    } else if (endedRemoteEvent == CallEvent.ENDED_REMOTE_GLARE && incomingBeforeAccept) {
      webRtcInteractor.insertMissedCall(remotePeer, remotePeer.getCallStartTimestamp(), currentState.getCallSetupState(remotePeer).isRemoteVideoOffer());
    } else if (endedRemoteEvent == CallEvent.ENDED_REMOTE_RECALL && incomingBeforeAccept) {
      webRtcInteractor.insertMissedCall(remotePeer, remotePeer.getCallStartTimestamp(), currentState.getCallSetupState(remotePeer).isRemoteVideoOffer());
    }

    if (state == WebRtcViewModel.State.CALL_ACCEPTED_ELSEWHERE) {
      webRtcInteractor.insertReceivedCall(remotePeer, currentState.getCallSetupState(remotePeer).isRemoteVideoOffer());
    }

    currentState = currentState.builder()
                               .changeCallInfoState()
                               .callState(state)
                               .build();

    webRtcInteractor.postStateUpdate(currentState);

    return terminate(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEnded(@NonNull WebRtcServiceState currentState, @NonNull CallEvent endedEvent, @NonNull RemotePeer remotePeer) {
    Log.i(tag, "handleEnded(): call_id: " + remotePeer.getCallId() + " action: " + endedEvent);

    if (remotePeer.callIdEquals(currentState.getCallInfoState().getActivePeer()) && !currentState.getCallInfoState().getCallState().isErrorState()) {
      currentState = currentState.builder()
                                 .changeCallInfoState()
                                 .callState(endedEvent == CallEvent.ENDED_TIMEOUT ? WebRtcViewModel.State.RECIPIENT_UNAVAILABLE : WebRtcViewModel.State.NETWORK_FAILURE)
                                 .build();

      webRtcInteractor.postStateUpdate(currentState);
    }

    if (remotePeer.getState() == CallState.ANSWERING || remotePeer.getState() == CallState.LOCAL_RINGING) {
      webRtcInteractor.insertMissedCall(remotePeer, remotePeer.getCallStartTimestamp(), currentState.getCallSetupState(remotePeer).isRemoteVideoOffer());
    }

    return terminate(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetupFailure(@NonNull WebRtcServiceState currentState, @NonNull CallId callId) {
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

      webRtcInteractor.postStateUpdate(currentState);

      if (activePeer.getState() == CallState.ANSWERING || activePeer.getState() == CallState.LOCAL_RINGING) {
        webRtcInteractor.insertMissedCall(activePeer, activePeer.getCallStartTimestamp(), currentState.getCallSetupState(activePeer).isRemoteVideoOffer());
      }

      return terminate(currentState, activePeer);
    } else {
      RemotePeer peerByCallId = currentState.getCallInfoState().getPeerByCallId(callId);
      if (peerByCallId != null) {
        webRtcInteractor.terminateCall(peerByCallId.getId());
      }
    }

    return currentState;
  }
}
