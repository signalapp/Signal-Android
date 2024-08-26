package org.thoughtcrime.securesms.service.webrtc;

import android.os.ResultReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.components.webrtc.EglBaseWrapper;
import org.thoughtcrime.securesms.database.CallTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData.CallMetadata;
import org.thoughtcrime.securesms.service.webrtc.state.CallSetupState;
import org.thoughtcrime.securesms.service.webrtc.state.VideoState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.thoughtcrime.securesms.util.NetworkUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.webrtc.PeerConnection;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;

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
  protected @NonNull WebRtcServiceState handleStartOutgoingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer, @NonNull OfferMessage.Type offerType) {
    Log.i(TAG, "handleStartOutgoingCall():");
    WebRtcServiceStateBuilder builder = currentState.builder();

    remotePeer.dialing();

    Log.i(TAG, "assign activePeer callId: " + remotePeer.getCallId() + " key: " + remotePeer.hashCode() + " type: " + offerType);

    boolean isVideoCall = offerType == OfferMessage.Type.VIDEO_CALL;

    webRtcInteractor.setCallInProgressNotification(TYPE_OUTGOING_RINGING, remotePeer, isVideoCall);
    webRtcInteractor.setDefaultAudioDevice(remotePeer.getId(),
                                           isVideoCall ? SignalAudioManager.AudioDevice.SPEAKER_PHONE : SignalAudioManager.AudioDevice.EARPIECE,
                                           false);
    webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context));
    webRtcInteractor.initializeAudioForCall();
    webRtcInteractor.startOutgoingRinger();

    if (!webRtcInteractor.addNewOutgoingCall(remotePeer.getId(), remotePeer.getCallId().longValue(), isVideoCall)) {
      Log.i(TAG, "Unable to add new outgoing call");
      return handleDropCall(currentState, remotePeer.getCallId().longValue());
    }

    RecipientUtil.setAndSendUniversalExpireTimerIfNecessary(context, Recipient.resolved(remotePeer.getId()), SignalDatabase.threads().getThreadIdIfExistsFor(remotePeer.getId()));

    SignalDatabase.calls().insertOneToOneCall(remotePeer.getCallId().longValue(),
                                              System.currentTimeMillis(),
                                              remotePeer.getId(),
                                      isVideoCall ? CallTable.Type.VIDEO_CALL : CallTable.Type.AUDIO_CALL,
                                              CallTable.Direction.OUTGOING,
                                              CallTable.Event.ONGOING);

    EglBaseWrapper.replaceHolder(EglBaseWrapper.OUTGOING_PLACEHOLDER, remotePeer.getCallId().longValue());

    webRtcInteractor.retrieveTurnServers(remotePeer);

    return builder.changeCallSetupState(remotePeer.getCallId())
                  .enableVideoOnCreate(isVideoCall)
                  .waitForTelecom(AndroidTelecomUtil.getTelecomSupported())
                  .telecomApproved(false)
                  .commit()
                  .changeCallInfoState()
                  .activePeer(remotePeer)
                  .callState(WebRtcViewModel.State.CALL_OUTGOING)
                  .commit()
                  .changeLocalDeviceState()
                  .build();
  }

  @Override
  public @NonNull WebRtcServiceState handleTurnServerUpdate(@NonNull WebRtcServiceState currentState,
                                                            @NonNull List<PeerConnection.IceServer> iceServers,
                                                            boolean isAlwaysTurn)
  {
    RemotePeer activePeer = currentState.getCallInfoState().requireActivePeer();

    Log.i(TAG, "handleTurnServerUpdate(): call_id: " + activePeer.getCallId());

    currentState = currentState.builder()
                               .changeCallSetupState(activePeer.getCallId())
                               .iceServers(iceServers)
                               .alwaysTurn(isAlwaysTurn)
                               .build();

    return proceed(currentState);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetTelecomApproved(@NonNull WebRtcServiceState currentState, long callId, RecipientId recipientId) {
    return proceed(super.handleSetTelecomApproved(currentState, callId, recipientId));
  }

  private @NonNull WebRtcServiceState proceed(@NonNull WebRtcServiceState currentState) {
    RemotePeer      activePeer      = currentState.getCallInfoState().requireActivePeer();
    CallSetupState  callSetupState  = currentState.getCallSetupState(activePeer);

    if (callSetupState.getIceServers().isEmpty() || (callSetupState.shouldWaitForTelecomApproval() && !callSetupState.isTelecomApproved())) {
      Log.i(TAG, "Unable to proceed without ice server and telecom approval" +
                 " iceServers: " + Util.hasItems(callSetupState.getIceServers()) +
                 " waitForTelecom: " + callSetupState.shouldWaitForTelecomApproval() +
                 " telecomApproved: " + callSetupState.isTelecomApproved());
      return currentState;
    }

    VideoState      videoState      = currentState.getVideoState();
    CallParticipant callParticipant = Objects.requireNonNull(currentState.getCallInfoState().getRemoteCallParticipant(activePeer.getRecipient()));

    try {
      webRtcInteractor.getCallManager().proceed(activePeer.getCallId(),
                                                context,
                                                videoState.getLockableEglBase().require(),
                                                RingRtcDynamicConfiguration.getAudioProcessingMethod(),
                                                RingRtcDynamicConfiguration.shouldUseOboeAdm(),
                                                videoState.requireLocalSink(),
                                                callParticipant.getVideoSink(),
                                                videoState.requireCamera(),
                                                callSetupState.getIceServers(),
                                                callSetupState.isAlwaysTurnServers(),
                                                NetworkUtil.getCallingDataMode(context),
                                                AUDIO_LEVELS_INTERVAL,
                                                currentState.getCallSetupState(activePeer).isEnableVideoOnCreate());
    } catch (CallException e) {
      return callFailure(currentState, "Unable to proceed with call: ", e);
    }

    return currentState.builder()
                       .changeLocalDeviceState()
                       .cameraState(currentState.getVideoState().requireCamera().getCameraState())
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleDropCall(@NonNull WebRtcServiceState currentState, long callId) {
    return callSetupDelegate.handleDropCall(currentState, callId);
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

    if (answerMetadata.getOpaque() == null) {
      return callFailure(currentState, "receivedAnswer() failed: answerMetadata did not contain opaque", null);
    }

    try {
      byte[] remoteIdentityKey = WebRtcUtil.getPublicKeyBytes(receivedAnswerMetadata.getRemoteIdentityKey());
      byte[] localIdentityKey  = WebRtcUtil.getPublicKeyBytes(SignalStore.account().getAciIdentityKey().getPublicKey().serialize());

      webRtcInteractor.getCallManager().receivedAnswer(callMetadata.getCallId(), callMetadata.getRemoteDevice(), answerMetadata.getOpaque(), remoteIdentityKey, localIdentityKey);
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
  protected @NonNull WebRtcServiceState handleRemoteVideoEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    return activeCallDelegate.handleRemoteVideoEnable(currentState, enable);
  }

  @Override
  protected @NonNull WebRtcServiceState handleScreenSharingEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    return activeCallDelegate.handleScreenSharingEnable(currentState, enable);
  }

  @Override
  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    RemotePeer activePeer = currentState.getCallInfoState().getActivePeer();
    if (activePeer != null) {
      webRtcInteractor.sendNotAcceptedCallEventSyncMessage(activePeer,
                                                           true,
                                                           currentState.getCallSetupState(activePeer).isAcceptWithVideo() || currentState.getLocalDeviceState().getCameraState().isEnabled());
    }

    return activeCallDelegate.handleLocalHangup(currentState);
  }

  @Override
  protected @NonNull WebRtcServiceState handleReceivedOfferWhileActive(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleReceivedOfferWhileActive(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEndedRemote(@NonNull WebRtcServiceState currentState, @NonNull CallManager.CallEvent endedRemoteEvent, @NonNull RemotePeer remotePeer) {
    RemotePeer activePeer = currentState.getCallInfoState().getActivePeer();
    if (activePeer != null &&
        (endedRemoteEvent == CallManager.CallEvent.ENDED_REMOTE_HANGUP ||
         endedRemoteEvent == CallManager.CallEvent.ENDED_REMOTE_HANGUP_NEED_PERMISSION ||
         endedRemoteEvent == CallManager.CallEvent.ENDED_REMOTE_BUSY ||
         endedRemoteEvent == CallManager.CallEvent.ENDED_TIMEOUT ||
         endedRemoteEvent == CallManager.CallEvent.ENDED_REMOTE_GLARE))
    {
      webRtcInteractor.sendNotAcceptedCallEventSyncMessage(activePeer,
                                                           true,
                                                           currentState.getCallSetupState(activePeer).isAcceptWithVideo() || currentState.getLocalDeviceState().getCameraState().isEnabled());
    }

    return activeCallDelegate.handleEndedRemote(currentState, endedRemoteEvent, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEnded(@NonNull WebRtcServiceState currentState, @NonNull CallManager.CallEvent endedEvent, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleEnded(currentState, endedEvent, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetupFailure(@NonNull WebRtcServiceState currentState, @NonNull CallId callId) {
    return activeCallDelegate.handleSetupFailure(currentState, callId);
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
