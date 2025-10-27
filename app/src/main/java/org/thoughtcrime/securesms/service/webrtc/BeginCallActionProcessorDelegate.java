package org.thoughtcrime.securesms.service.webrtc;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.database.CallTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.nio.ByteBuffer;

import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_CONNECTING;

/**
 * Encapsulates the logic to begin a 1:1 call from scratch. Other action processors
 * delegate the appropriate action to it but it is not intended to be the main processor for the system.
 */
public class BeginCallActionProcessorDelegate extends WebRtcActionProcessor {

  public BeginCallActionProcessorDelegate(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    super(webRtcInteractor, tag);
  }

  @Override
  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState,
                                                           @NonNull RemotePeer remotePeer,
                                                           @NonNull OfferMessage.Type offerType)
  {
    if (!remotePeer.getRecipient().getHasAci()) {
      Log.w(tag, "1:1 outgoing recipient is PNI only, send pseudo-call offer and terminate call");

      remotePeer.setCallId(new CallId(ByteBuffer.wrap(Util.getSecretBytes(8)).getLong()));

      currentState = currentState.builder()
                                 .actionProcessor(new IdleActionProcessor(webRtcInteractor))
                                 .changeCallInfoState()
                                 .callRecipient(remotePeer.getRecipient())
                                 .callState(WebRtcViewModel.State.CALL_NEEDS_PERMISSION)
                                 .putParticipant(remotePeer.getRecipient(), CallParticipant.EMPTY)
                                 .build();

      boolean isVideoOffer = OfferMessage.Type.VIDEO_CALL == offerType;

      SignalDatabase.calls().insertOneToOneCall(remotePeer.getCallId().longValue(),
                                                System.currentTimeMillis(),
                                                remotePeer.getId(),
                                                isVideoOffer ? CallTable.Type.VIDEO_CALL : CallTable.Type.AUDIO_CALL,
                                                CallTable.Direction.OUTGOING,
                                                CallTable.Event.ONGOING);

      webRtcInteractor.insertMissedCall(remotePeer, System.currentTimeMillis(), isVideoOffer, CallTable.Event.NOT_ACCEPTED);
      webRtcInteractor.postStateUpdate(currentState);
      webRtcInteractor.sendCallMessage(remotePeer, SignalServiceCallMessage.forOffer(new OfferMessage(remotePeer.getCallId().longValue(),
                                                                                                      offerType,
                                                                                                      new byte[0]),
                                                                                     null));

      return terminate(currentState, remotePeer);
    }

    remotePeer.setCallStartTimestamp(System.currentTimeMillis());

    currentState = currentState.builder()
                               .actionProcessor(new OutgoingCallActionProcessor(webRtcInteractor))
                               .changeCallInfoState()
                               .callRecipient(remotePeer.getRecipient())
                               .callState(WebRtcViewModel.State.CALL_OUTGOING)
                               .putRemotePeer(remotePeer)
                               .putParticipant(remotePeer.getRecipient(),
                                               CallParticipant.createRemote(new CallParticipantId(remotePeer.getRecipient()),
                                                                            remotePeer.getRecipient(),
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

    CallManager.CallMediaType callMediaType = WebRtcUtil.getCallMediaTypeFromOfferType(offerType);
    try {
      webRtcInteractor.getCallManager().call(remotePeer, callMediaType, SignalStore.account().getDeviceId());
    } catch (CallException e) {
      return callFailure(currentState, "Unable to create outgoing call: ", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleStartIncomingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer, @NonNull OfferMessage.Type offerType) {
    remotePeer.answering();

    Log.i(tag, "assign activePeer callId: " + remotePeer.getCallId() + " key: " + remotePeer.hashCode());


    boolean    isRemoteVideoOffer = currentState.getCallSetupState(remotePeer).isRemoteVideoOffer();

    webRtcInteractor.setCallInProgressNotification(TYPE_INCOMING_CONNECTING, remotePeer, isRemoteVideoOffer);
    webRtcInteractor.retrieveTurnServers(remotePeer);
    webRtcInteractor.initializeAudioForCall();

    if (!webRtcInteractor.addNewIncomingCall(remotePeer.getId(), remotePeer.getCallId().longValue(), offerType == OfferMessage.Type.VIDEO_CALL)) {
      Log.i(tag, "Unable to add new incoming call");
      return handleDropCall(currentState, remotePeer.getCallId().longValue());
    }

    return currentState.builder()
                       .actionProcessor(new IncomingCallActionProcessor(webRtcInteractor))
                       .changeCallSetupState(remotePeer.getCallId())
                       .waitForTelecom(AndroidTelecomUtil.getTelecomSupported())
                       .telecomApproved(false)
                       .commit()
                       .changeCallInfoState()
                       .callRecipient(remotePeer.getRecipient())
                       .activePeer(remotePeer)
                       .callState(WebRtcViewModel.State.CALL_INCOMING)
                       .putParticipant(remotePeer.getRecipient(),
                                       CallParticipant.createRemote(new CallParticipantId(remotePeer.getRecipient()),
                                                                    remotePeer.getRecipient(),
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
}
