package org.thoughtcrime.securesms.service.webrtc;

import android.net.Uri;
import android.os.ResultReceiver;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.models.recipients.RecipientId;
import org.signal.core.util.logging.Log;
import org.signal.remoteconfig.RemoteConfig;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.database.CallTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.DoNotDisturbUtil;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.CallState;
import org.thoughtcrime.securesms.ringrtc.OutgoingVideoSourceRouter;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.CallSetupState;
import org.thoughtcrime.securesms.service.webrtc.state.VideoState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.signal.core.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.NetworkUtil;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.webrtc.PeerConnection;

import java.util.List;
import java.util.Objects;

import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_RINGING;

/**
 * Responsible for setting up and managing the start of an incoming 1:1 call. Transitioned
 * to from idle or pre-join and can either move to a connected state (user picks up) or
 * a disconnected state (remote hangup, local hangup, etc.).
 */
public class IncomingCallActionProcessor extends DeviceAwareActionProcessor {

  private static final String TAG = Log.tag(IncomingCallActionProcessor.class);

  private final ActiveCallActionProcessorDelegate activeCallDelegate;
  private final CallSetupActionProcessorDelegate  callSetupDelegate;

  public IncomingCallActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
    activeCallDelegate = new ActiveCallActionProcessorDelegate(webRtcInteractor, TAG);
    callSetupDelegate  = new CallSetupActionProcessorDelegate(webRtcInteractor, TAG);
  }

  @Override
  protected @NonNull WebRtcServiceState handleIsInCallQuery(@NonNull WebRtcServiceState currentState, @Nullable ResultReceiver resultReceiver) {
    return activeCallDelegate.handleIsInCallQuery(currentState, resultReceiver);
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
    currentState = super.handleSetTelecomApproved(currentState, callId, recipientId);

    RemotePeer activePeer = currentState.getCallInfoState().getActivePeer();
    if (activePeer == null || activePeer.getCallId().longValue() != callId || activePeer.getState() != CallState.LOCAL_RINGING) {
      return currentState;
    }

    Log.i(TAG, "handleSetTelecomApproved(): ringing now that telecom has added the call");
    return startRinging(currentState, activePeer);
  }

  private @NonNull WebRtcServiceState proceed(@NonNull WebRtcServiceState currentState) {
    RemotePeer     activePeer     = currentState.getCallInfoState().requireActivePeer();
    CallSetupState callSetupState = currentState.getCallSetupState(activePeer.getCallId());

    if (callSetupState.getIceServers().isEmpty()) {
      Log.i(TAG, "Unable to proceed without ice servers");
      return currentState;
    }

    byte            dredDuration    = (byte) RemoteConfig.dredDuration();
    boolean         hideIp          = !activePeer.getRecipient().isProfileSharing() || callSetupState.isAlwaysTurnServers();
    VideoState      videoState      = currentState.getVideoState();
    CallParticipant callParticipant = Objects.requireNonNull(currentState.getCallInfoState().getRemoteCallParticipant(activePeer.getRecipient()));

    try {
      webRtcInteractor.getCallManager().proceed(activePeer.getCallId(),
                                                context,
                                                videoState.getLockableEglBase().require(),
                                                RingRtcDynamicConfiguration.getAudioConfig(),
                                                RingRtcDynamicConfiguration.getVideoConfig(),
                                                videoState.requireLocalSink(),
                                                callParticipant.getVideoSink(),
                                                videoState.requireRouter(),
                                                callSetupState.getIceServers(),
                                                hideIp,
                                                NetworkUtil.getCallingDataMode(context),
                                                AUDIO_LEVELS_INTERVAL,
                                                dredDuration,
                                                false,
                                                RingRtcDynamicConfiguration.getStatsIntervalSecs());
    } catch (CallException e) {
      return callFailure(currentState, "Unable to proceed with call: ", e);
    }

    webRtcInteractor.updatePhoneState(LockManager.PhoneState.PROCESSING);
    webRtcInteractor.postStateUpdate(currentState);

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleDropCall(@NonNull WebRtcServiceState currentState, long callId, boolean busy) {
    return callSetupDelegate.handleDropCall(currentState, callId, busy);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetMuteAudio(@NonNull WebRtcServiceState currentState, boolean muted) {
    return activeCallDelegate.handleSetMuteAudio(currentState, muted);
  }

  @Override
  protected @NonNull WebRtcServiceState handleAcceptCall(@NonNull WebRtcServiceState currentState, boolean answerWithVideo) {
    RemotePeer activePeer = currentState.getCallInfoState().requireActivePeer();

    Log.i(TAG, "handleAcceptCall(): call_id: " + activePeer.getCallId() + " answerWithVideo: " + answerWithVideo);

    OutgoingVideoSourceRouter router = currentState.getVideoState().requireRouter();
    router.setVanitySink(null);

    if (!answerWithVideo && currentState.getLocalDeviceState().getCameraState().isEnabled()) {
      router.setEnabled(false);
      currentState = currentState.builder()
                                 .changeLocalDeviceState()
                                 .cameraState(router.getCameraState())
                                 .build();
    }

    currentState = currentState.builder()
                               .changeCallSetupState(activePeer.getCallId())
                               .acceptWithVideo(answerWithVideo)
                               .accepted(true)
                               .waitForAudio(true)
                               .build();

    if (currentState.getCallSetupState(activePeer).usesTelecom() && currentState.getCallSetupState(activePeer).isTelecomApproved()) {
      webRtcInteractor.activateCall(activePeer.getId(), activePeer.getCallId().longValue(), answerWithVideo);
    }

    boolean waitForBluetooth = currentState.getCallSetupState(activePeer).usesTelecom() && currentState.getLocalDeviceState().getActiveDevice() == SignalAudioManager.AudioDevice.BLUETOOTH;
    webRtcInteractor.prepareAudioForAccept(waitForBluetooth);

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleAudioReadyForAccept(@NonNull WebRtcServiceState currentState) {
    RemotePeer activePeer = currentState.getCallInfoState().getActivePeer();

    if (activePeer == null || !currentState.getCallSetupState(activePeer.getCallId()).shouldWaitForAudio()) {
      Log.w(TAG, "handleAudioReadyForAccept(): call is no longer waiting to be accepted, ignoring");
      return currentState;
    }

    Log.i(TAG, "handleAudioReadyForAccept(): call_id: " + activePeer.getCallId());

    currentState = currentState.builder()
                               .changeCallSetupState(activePeer.getCallId())
                               .waitForAudio(false)
                               .build();

    if (activePeer.getState() == CallState.ANSWERING || (currentState.getCallSetupState(activePeer).usesTelecom() && !currentState.getCallSetupState(activePeer).isTelecomApproved())) {
      Log.i(TAG, "handleAudioReadyForAccept(): accepted before ringing, deferring until ringing");
      return currentState;
    }

    try {
      webRtcInteractor.getCallManager().acceptCall(activePeer.getCallId());
    } catch (CallException e) {
      return callFailure(currentState, "accept() failed: ", e);
    }

    return currentState;
  }

  protected @NonNull WebRtcServiceState handleDenyCall(@NonNull WebRtcServiceState currentState) {
    RemotePeer activePeer = currentState.getCallInfoState().requireActivePeer();

    if (activePeer.getState() == CallState.ANSWERING && currentState.getCallSetupState(activePeer).usesTelecom() && !currentState.getCallSetupState(activePeer).isAccepted()) {
      Log.i(TAG, "handleDenyCall(): denied before local ringing, deferring until ringing");
      return currentState.builder()
                         .changeCallSetupState(activePeer.getCallId())
                         .denyPending(true)
                         .build();
    }

    if (activePeer.getState() != CallState.LOCAL_RINGING) {
      Log.w(TAG, "Can only deny from ringing!");
      return currentState;
    }

    // Reject deny if the call has already been fully accepted by RingRTC
    if (currentState.getCallSetupState(activePeer).isAccepted() && !currentState.getCallSetupState(activePeer).shouldWaitForAudio()) {
      Log.w(TAG, "Cannot deny after call has been accepted!");
      return currentState;
    }

    Log.i(TAG, "handleDenyCall():");

    return rejectAndTerminate(currentState, activePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    RemotePeer activePeer = currentState.getCallInfoState().getActivePeer();

    if (activePeer == null) {
      Log.i(TAG, "handleLocalHangup(): no active peer");
      return currentState;
    }

    if (activePeer.getState() == CallState.ANSWERING) {
      Log.i(TAG, "handleLocalHangup(): hangup before local ringing, deferring until ringing");
      return currentState.builder()
                         .changeCallSetupState(activePeer.getCallId())
                         .denyPending(true)
                         .build();
    }

    if (currentState.getCallSetupState(activePeer).isAccepted()) {
      Log.i(TAG, "handleLocalHangup(): call_id: " + activePeer.getCallId() + " already accepted, hanging up");
      webRtcInteractor.sendAcceptedCallEventSyncMessage(activePeer, false, currentState.getCallSetupState(activePeer).isAcceptWithVideo());
      return activeCallDelegate.handleLocalHangup(currentState);
    }

    Log.i(TAG, "handleLocalHangup(): call_id: " + activePeer.getCallId());

    return rejectAndTerminate(currentState, activePeer);
  }

  private @NonNull WebRtcServiceState rejectAndTerminate(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer activePeer) {
    OutgoingVideoSourceRouter router = currentState.getVideoState().getRouter();
    if (router != null) {
      router.setVanitySink(null);
    }

    webRtcInteractor.sendNotAcceptedCallEventSyncMessage(activePeer,
                                                         false,
                                                         currentState.getCallSetupState(activePeer).isRemoteVideoOffer());

    try {
      webRtcInteractor.rejectIncomingCall(activePeer.getId(), activePeer.getCallId().longValue());
      webRtcInteractor.getCallManager().hangup();

      currentState = currentState.builder()
                                 .changeCallInfoState()
                                 .callState(WebRtcViewModel.State.CALL_DISCONNECTED)
                                 .build();

      webRtcInteractor.postStateUpdate(currentState);

      return terminate(currentState, activePeer);
    } catch (CallException e) {
      return callFailure(currentState, "hangup() failed: ", e);
    }
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetIncomingRingingVanity(@NonNull WebRtcServiceState currentState, boolean enabled) {
    RemotePeer     activePeer     = currentState.getCallInfoState().requireActivePeer();
    CallSetupState callSetupState = currentState.getCallSetupState(activePeer);

    if (!callSetupState.isRemoteVideoOffer()) {
      return currentState;
    }

    if (callSetupState.isAccepted()) {
      Log.w(TAG, "handleSetIncomingRingingVanity(): call has already been accepted, ignoring");
      return currentState;
    }

    boolean cameraAlreadyEnabled = currentState.getLocalDeviceState().getCameraState().isEnabled();

    if (enabled && cameraAlreadyEnabled) {
      return currentState;
    }

    if (!enabled && !cameraAlreadyEnabled) {
      return currentState;
    }

    OutgoingVideoSourceRouter router = currentState.getVideoState().requireRouter();

    if (enabled) {
      Log.i(TAG, "handleSetIncomingRingingVanity(): enabling vanity camera");
      router.setVanitySink(currentState.getVideoState().requireLocalSink());
      router.setEnabled(true);
    } else {
      Log.i(TAG, "handleSetIncomingRingingVanity(): disabling vanity camera");
      router.setVanitySink(null);
      router.setEnabled(false);
    }

    return currentState.builder()
                       .changeLocalDeviceState()
                       .cameraState(router.getCameraState())
                       .build();
  }

  protected @NonNull WebRtcServiceState handleLocalRinging(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    RemotePeer activePeer                = currentState.getCallInfoState().requireActivePeer();
    Recipient  recipient                 = remotePeer.getRecipient();
    boolean    shouldDisturbUserWithCall = DoNotDisturbUtil.shouldDisturbUserWithCall(context.getApplicationContext(), recipient);

    Log.i(TAG, "handleLocalRinging(): call_id: " + remotePeer.getCallId() + " shouldDisturbUser: " + shouldDisturbUserWithCall);

    activePeer.localRinging();

    SignalDatabase.calls().insertOneToOneCall(remotePeer.getCallId().longValue(),
                                              System.currentTimeMillis(),
                                              remotePeer.getId(),
                                              currentState.getCallSetupState(activePeer).isRemoteVideoOffer() ? CallTable.Type.VIDEO_CALL : CallTable.Type.AUDIO_CALL,
                                              CallTable.Direction.INCOMING,
                                              CallTable.Event.ONGOING,
                                              false);

    if (currentState.getCallSetupState(activePeer).isDenyPending()) {
      if (currentState.getCallSetupState(activePeer).isAccepted()) {
        Log.i(TAG, "handleLocalRinging(): applying hangup of an accepted call received before ringing");
        webRtcInteractor.sendAcceptedCallEventSyncMessage(activePeer, false, currentState.getCallSetupState(activePeer).isAcceptWithVideo());
        return activeCallDelegate.handleLocalHangup(currentState);
      }

      Log.i(TAG, "handleLocalRinging(): applying deny received before ringing");
      return rejectAndTerminate(currentState, activePeer);
    }

    CallSetupState callSetupState = currentState.getCallSetupState(activePeer);
    if (callSetupState.usesTelecom() && !callSetupState.isTelecomApproved()) {
      Log.i(TAG, "handleLocalRinging(): adding call to telecom, ringing once it is added");
      webRtcInteractor.addNewIncomingCall(activePeer.getId(), activePeer.getCallId().longValue(), callSetupState.isRemoteVideoOffer());
      return currentState;
    }

    return startRinging(currentState, activePeer);
  }

  private @NonNull WebRtcServiceState startRinging(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer activePeer) {
    Recipient recipient                 = activePeer.getRecipient();
    boolean   shouldDisturbUserWithCall = DoNotDisturbUtil.shouldDisturbUserWithCall(context.getApplicationContext(), recipient);

    if (currentState.getCallSetupState(activePeer).isAccepted()) {
      currentState = currentState.builder()
                                 .changeCallInfoState()
                                 .callState(WebRtcViewModel.State.CALL_INCOMING)
                                 .build();

      if (currentState.getCallSetupState(activePeer).usesTelecom()) {
        webRtcInteractor.activateCall(activePeer.getId(), activePeer.getCallId().longValue(), currentState.getCallSetupState(activePeer).isAcceptWithVideo());

        if (!currentState.getCallSetupState(activePeer).shouldWaitForAudio()) {
          Log.i(TAG, "handleLocalRinging(): accepted before telecom approval, waiting for telecom audio again");
          currentState = currentState.builder()
                                     .changeCallSetupState(activePeer.getCallId())
                                     .waitForAudio(true)
                                     .build();

          webRtcInteractor.prepareAudioForAccept(currentState.getLocalDeviceState().getActiveDevice() == SignalAudioManager.AudioDevice.BLUETOOTH);
        }
      }

      if (currentState.getCallSetupState(activePeer).shouldWaitForAudio()) {
        Log.i(TAG, "handleLocalRinging(): accepted before ringing, accepting once audio is ready");
        return currentState;
      }

      Log.i(TAG, "handleLocalRinging(): applying accept received before ringing");
      try {
        webRtcInteractor.getCallManager().acceptCall(activePeer.getCallId());
      } catch (CallException e) {
        return callFailure(currentState, "accept() failed: ", e);
      }
      return currentState;
    }

    if (!shouldDisturbUserWithCall) {
      Log.i(TAG, "Silently ignoring call due to mute settings.");
      return currentState.builder()
                         .changeCallInfoState()
                         .callState(WebRtcViewModel.State.CALL_INCOMING)
                         .build();
    }

    webRtcInteractor.updatePhoneState(LockManager.PhoneState.INTERACTIVE);
    boolean started = webRtcInteractor.startWebRtcCallActivityIfPossible();
    if (!started) {
      Log.i(TAG, "Unable to start call activity due to OS version or not being in the foreground");
      AppForegroundObserver.addListener(webRtcInteractor.getForegroundListener());
    }

    boolean isCallNotificationsEnabled = SignalStore.settings().isCallNotificationsEnabled() && NotificationChannels.getInstance().areNotificationsEnabled();
    if (isCallNotificationsEnabled) {
      Uri                         ringtone     = recipient.resolve().getCallRingtone();
      RecipientTable.VibrateState vibrateState = recipient.resolve().getCallVibrate();

      if (ringtone == null) {
        ringtone = SignalStore.settings().getCallRingtone();
      }

      if (TextUtils.isEmpty(ringtone.toString())) {
        Log.i(TAG, "Ringtone is likely set to silent");
        ringtone = null;
      }

      webRtcInteractor.startIncomingRinger(ringtone, vibrateState == RecipientTable.VibrateState.ENABLED || (vibrateState == RecipientTable.VibrateState.DEFAULT && SignalStore.settings().isCallVibrateEnabled()));
    }

    CallSetupState callSetupState = currentState.getCallSetupState(activePeer);

    webRtcInteractor.setCallInProgressNotification(TYPE_INCOMING_RINGING, activePeer, callSetupState.isRemoteVideoOffer(), callSetupState.usesTelecom());
    webRtcInteractor.registerPowerButtonReceiver();

    return currentState.builder()
                       .changeCallInfoState()
                       .callState(WebRtcViewModel.State.CALL_INCOMING)
                       .build();
  }

  protected @NonNull WebRtcServiceState handleScreenOffChange(@NonNull WebRtcServiceState currentState) {
    Log.i(TAG, "Silencing incoming ringer...");

    webRtcInteractor.silenceIncomingRinger();
    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleRemoteAudioEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    return activeCallDelegate.handleRemoteAudioEnable(currentState, enable);
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
  protected @NonNull WebRtcServiceState handleReceivedOfferWhileActive(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleReceivedOfferWhileActive(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEndedRemote(@NonNull WebRtcServiceState currentState, @NonNull CallManager.CallEndReason callEndReason, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleEndedRemote(currentState, callEndReason, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleEnded(@NonNull WebRtcServiceState currentState, @NonNull CallManager.CallEndReason callEndReason, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleEnded(currentState, callEndReason, remotePeer);
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
