package org.thoughtcrime.securesms.service.webrtc;

import android.media.AudioManager;
import android.os.ResultReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.ringrtc.IceCandidateParcel;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.CallMetadata;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.OfferMetadata;
import org.thoughtcrime.securesms.service.webrtc.state.VideoState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.webrtc.audio.OutgoingRinger;
import org.webrtc.PeerConnection;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_OUTGOING_RINGING;

/**
 * Responsible for setting up and managing the start of an outgoing 1:1 call. Transitioned
 * to from idle or pre-join and can either move to a connected state (callee picks up) or
 * a disconnected state (remote hangup, local hangup, etc.).
 */
public class OutgoingCallActionProcessor extends DeviceAwareActionProcessor {

  private static final String TAG = Log.tag(OutgoingCallActionProcessor.class);

  private final ActiveCallActionProcessorDelegate activeCallDelegate;
  private final CallSetupActionProcessorDelegate  callSetupDelegate;

  public OutgoingCallActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
    activeCallDelegate = new ActiveCallActionProcessorDelegate(webRtcInteractor, TAG);
    callSetupDelegate  = new CallSetupActionProcessorDelegate(webRtcInteractor, TAG);
  }

  @Override
  protected @NonNull WebRtcServiceState handleIsInCallQuery(@NonNull WebRtcServiceState currentState, @Nullable ResultReceiver resultReceiver) {
    return activeCallDelegate.handleIsInCallQuery(currentState, resultReceiver);
  }

  @Override
  protected @NonNull WebRtcServiceState handleStartOutgoingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handleStartOutgoingCall():");
    WebRtcServiceStateBuilder builder = currentState.builder();

    remotePeer.dialing();

    Log.i(TAG, "assign activePeer callId: " + remotePeer.getCallId() + " key: " + remotePeer.hashCode());

    AudioManager androidAudioManager = ServiceUtil.getAudioManager(context);
    androidAudioManager.setSpeakerphoneOn(false);
    WebRtcUtil.enableSpeakerPhoneIfNeeded(context, currentState.getCallSetupState().isEnableVideoOnCreate());

    webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context));
    webRtcInteractor.initializeAudioForCall();
    webRtcInteractor.startOutgoingRinger(OutgoingRinger.Type.RINGING);
    webRtcInteractor.setWantsBluetoothConnection(true);

    webRtcInteractor.setCallInProgressNotification(TYPE_OUTGOING_RINGING, remotePeer);

    DatabaseFactory.getSmsDatabase(context).insertOutgoingCall(remotePeer.getId(), currentState.getCallSetupState().isEnableVideoOnCreate());

    webRtcInteractor.retrieveTurnServers(remotePeer);

    return builder.changeCallInfoState()
                  .activePeer(remotePeer)
                  .callState(WebRtcViewModel.State.CALL_OUTGOING)
                  .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleSendOffer(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata, @NonNull OfferMetadata offerMetadata, boolean broadcast) {
    Log.i(TAG, "handleSendOffer(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    OfferMessage             offerMessage        = new OfferMessage(callMetadata.getCallId().longValue(), offerMetadata.getSdp(), offerMetadata.getOfferType(), offerMetadata.getOpaque());
    Integer                  destinationDeviceId = broadcast ? null : callMetadata.getRemoteDevice();
    SignalServiceCallMessage callMessage         = SignalServiceCallMessage.forOffer(offerMessage, true, destinationDeviceId);

    webRtcInteractor.sendCallMessage(callMetadata.getRemotePeer(), callMessage);

    return currentState;
  }

  @Override
  public @NonNull WebRtcServiceState handleTurnServerUpdate(@NonNull WebRtcServiceState currentState,
                                                            @NonNull List<PeerConnection.IceServer> iceServers,
                                                            boolean isAlwaysTurn)
  {
    try {
      VideoState      videoState      = currentState.getVideoState();
      RemotePeer      activePeer      = currentState.getCallInfoState().requireActivePeer();
      CallParticipant callParticipant = Objects.requireNonNull(currentState.getCallInfoState().getRemoteCallParticipant(activePeer.getRecipient()));

      webRtcInteractor.getCallManager().proceed(activePeer.getCallId(),
                                                context,
                                                videoState.requireEglBase(),
                                                videoState.requireLocalSink(),
                                                callParticipant.getVideoSink(),
                                                videoState.requireCamera(),
                                                iceServers,
                                                isAlwaysTurn,
                                                currentState.getCallSetupState().isEnableVideoOnCreate());
    } catch (CallException e) {
      return callFailure(currentState, "Unable to proceed with call: ", e);
    }

    return currentState.builder()
                       .changeLocalDeviceState()
                       .cameraState(currentState.getVideoState().requireCamera().getCameraState())
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleRemoteRinging(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handleRemoteRinging(): call_id: " + remotePeer.getCallId());

    currentState.getCallInfoState().requireActivePeer().remoteRinging();
    return currentState.builder()
                       .changeCallInfoState()
                       .callState(WebRtcViewModel.State.CALL_RINGING)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedAnswer(@NonNull WebRtcServiceState currentState,
                                                             @NonNull CallMetadata callMetadata,
                                                             @NonNull WebRtcData.AnswerMetadata answerMetadata,
                                                             @NonNull WebRtcData.ReceivedAnswerMetadata receivedAnswerMetadata)
  {
    Log.i(TAG, "handleReceivedAnswer(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    try {
      byte[] remoteIdentityKey = WebRtcUtil.getPublicKeyBytes(receivedAnswerMetadata.getRemoteIdentityKey());
      byte[] localIdentityKey  = WebRtcUtil.getPublicKeyBytes(IdentityKeyUtil.getIdentityKey(context).serialize());

      webRtcInteractor.getCallManager().receivedAnswer(callMetadata.getCallId(), callMetadata.getRemoteDevice(), answerMetadata.getOpaque(), answerMetadata.getSdp(), receivedAnswerMetadata.isMultiRing(), remoteIdentityKey, localIdentityKey);
    } catch (CallException | InvalidKeyException e) {
      return callFailure(currentState, "receivedAnswer() failed: ", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedBusy(@NonNull WebRtcServiceState currentState, @NonNull CallMetadata callMetadata) {
    Log.i(TAG, "handleReceivedBusy(): id: " + callMetadata.getCallId().format(callMetadata.getRemoteDevice()));

    try {
      webRtcInteractor.getCallManager().receivedBusy(callMetadata.getCallId(), callMetadata.getRemoteDevice());
    } catch (CallException e) {
      return callFailure(currentState, "receivedBusy() failed: ", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetMuteAudio(@NonNull WebRtcServiceState currentState, boolean muted) {
    return currentState.builder()
                       .changeLocalDeviceState()
                       .isMicrophoneEnabled(!muted)
                       .build();
  }

  @Override
  protected @NonNull  WebRtcServiceState handleRemoteVideoEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    return activeCallDelegate.handleRemoteVideoEnable(currentState, enable);
  }

  @Override
  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    return activeCallDelegate.handleLocalHangup(currentState);
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedOfferWhileActive(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleReceivedOfferWhileActive(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEndedRemote(@NonNull WebRtcServiceState currentState, @NonNull String action, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleEndedRemote(currentState, action, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEnded(@NonNull WebRtcServiceState currentState, @NonNull String action, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleEnded(currentState, action, remotePeer);
  }

  @Override
  protected  @NonNull WebRtcServiceState handleSetupFailure(@NonNull WebRtcServiceState currentState, @NonNull CallId callId) {
    return activeCallDelegate.handleSetupFailure(currentState, callId);
  }

  @Override
  protected @NonNull WebRtcServiceState handleCallConcluded(@NonNull WebRtcServiceState currentState, @Nullable RemotePeer remotePeer) {
    return activeCallDelegate.handleCallConcluded(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSendIceCandidates(@NonNull WebRtcServiceState currentState,
                                                                @NonNull WebRtcData.CallMetadata callMetadata,
                                                                boolean broadcast,
                                                                @NonNull ArrayList<IceCandidateParcel> iceCandidates)
  {
    return activeCallDelegate.handleSendIceCandidates(currentState, callMetadata, broadcast, iceCandidates);
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedIceCandidates(@NonNull WebRtcServiceState currentState,
                                                                    @NonNull WebRtcData.CallMetadata callMetadata,
                                                                    @NonNull ArrayList<IceCandidateParcel> iceCandidateParcels)
  {
    return activeCallDelegate.handleReceivedIceCandidates(currentState, callMetadata, iceCandidateParcels);
  }

  @Override
  public @NonNull WebRtcServiceState handleCallConnected(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    return callSetupDelegate.handleCallConnected(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    return callSetupDelegate.handleSetEnableVideo(currentState, enable);
  }
}
