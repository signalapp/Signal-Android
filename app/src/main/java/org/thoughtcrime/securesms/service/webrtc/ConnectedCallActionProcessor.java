package org.thoughtcrime.securesms.service.webrtc;

import android.content.Intent;
import android.os.ResultReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.OutgoingVideoSourceRouter;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.LocalDeviceState;
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcEphemeralState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;

import java.util.Collections;
import java.util.Optional;

/**
 * Handles action for a connected/ongoing call. At this point it's mostly responding
 * to user actions (local and remote) on video/mic and adjusting accordingly.
 */
public class ConnectedCallActionProcessor extends DeviceAwareActionProcessor {

  private static final String TAG = Log.tag(ConnectedCallActionProcessor.class);

  private final ActiveCallActionProcessorDelegate activeCallDelegate;

  public ConnectedCallActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
    activeCallDelegate = new ActiveCallActionProcessorDelegate(webRtcInteractor, TAG);
  }

  @Override
  protected @NonNull WebRtcServiceState handleIsInCallQuery(@NonNull WebRtcServiceState currentState, @Nullable ResultReceiver resultReceiver) {
    return activeCallDelegate.handleIsInCallQuery(currentState, resultReceiver);
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(TAG, "handleSetEnableVideo(): call_id: " + currentState.getCallInfoState().requireActivePeer().getCallId());

    try {
      webRtcInteractor.getCallManager().setVideoEnable(enable, false);
    } catch (CallException e) {
      return callFailure(currentState, "setVideoEnable() failed: ", e);
    }

    currentState = currentState.builder()
                               .changeLocalDeviceState()
                               .cameraState(currentState.getVideoState().requireRouter().getCameraState())
                               .build();

    boolean localVideoEnabled  = currentState.getLocalDeviceState().getCameraState().isEnabled() || currentState.getLocalDeviceState().isScreenSharing();
    boolean remoteVideoEnabled = currentState.getCallInfoState().getRemoteCallParticipantsMap().values().stream().anyMatch(CallParticipant::isVideoEnabled);
    webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context, localVideoEnabled, remoteVideoEnabled));

    WebRtcUtil.enableSpeakerPhoneIfNeeded(webRtcInteractor, currentState);

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetMuteAudio(@NonNull WebRtcServiceState currentState, boolean muted) {
    currentState = currentState.builder()
                               .changeLocalDeviceState()
                               .isMicrophoneEnabled(!muted)
                               .build();

    try {
      webRtcInteractor.getCallManager().setAudioEnable(currentState.getLocalDeviceState().isMicrophoneEnabled());
    } catch (CallException e) {
      return callFailure(currentState, "Enabling audio failed: ", e);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcEphemeralState handleAudioLevelsChanged(@NonNull WebRtcServiceState   currentState,
                                                                   @NonNull WebRtcEphemeralState ephemeralState,
                                                                            int                  localLevel,
                                                                            int                  remoteLevel) {
    Optional<CallParticipantId> callParticipantId = currentState.getCallInfoState()
                                                                .getRemoteCallParticipantsMap()
                                                                .keySet()
                                                                .stream()
                                                                .findFirst();

    return ephemeralState.copy(
        CallParticipant.AudioLevel.fromRawAudioLevel(localLevel),
        callParticipantId.map(participantId -> Collections.singletonMap(participantId, CallParticipant.AudioLevel.fromRawAudioLevel(remoteLevel)))
                         .orElse(Collections.emptyMap()),
        ephemeralState.getUnexpiredReactions()
    );
  }

  @Override
  public @NonNull WebRtcServiceState handleCallReconnect(@NonNull WebRtcServiceState currentState, @NonNull CallManager.CallEvent event) {
    Log.i(TAG, "handleCallReconnect(): event: " + event);

    return currentState.builder()
                       .changeCallInfoState()
                       .callState(event == CallManager.CallEvent.RECONNECTING ? WebRtcViewModel.State.CALL_RECONNECTING : WebRtcViewModel.State.CALL_CONNECTED)
                       .build();
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
  protected @NonNull WebRtcServiceState handleSetLocalScreenShare(@NonNull WebRtcServiceState currentState, boolean enable, @Nullable Intent mediaProjectionData) {
    Log.i(TAG, "handleSetLocalScreenShare(): enable: " + enable);

    OutgoingVideoSourceRouter router = currentState.getVideoState().requireRouter();

    RecipientId recipientId = currentState.getCallInfoState().requireActivePeer().getRecipient().getId();

    if (enable && mediaProjectionData != null) {
      Log.i(tag, "Updating service for media projection, then can screen share");
      ActiveCallManager.ActiveCallForegroundService.update(context, CallNotificationBuilder.TYPE_ESTABLISHED, recipientId, true, true);

      return currentState.builder()
                         .changeLocalDeviceState()
                         .setMediaProjectionIntent(mediaProjectionData)
                         .build();
    } else {
      router.stopScreenShare();

      boolean cameraWasEnabled = currentState.getLocalDeviceState().getCameraState().isEnabled();
      ActiveCallManager.ActiveCallForegroundService.update(context, CallNotificationBuilder.TYPE_ESTABLISHED, recipientId, cameraWasEnabled, false);

      try {
        webRtcInteractor.getCallManager().setVideoEnable(cameraWasEnabled, false);
      } catch (CallException e) {
        return callFailure(currentState, "setVideoEnable() after screen share failed: ", e);
      }

      currentState = currentState.builder()
                                 .changeLocalDeviceState()
                                 .isScreenSharing(false)
                                 .setMediaProjectionIntent(null)
                                 .build();

      boolean remoteVideoEnabled = currentState.getCallInfoState().getRemoteCallParticipantsMap().values().stream().anyMatch(CallParticipant::isVideoEnabled);
      webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context, cameraWasEnabled, remoteVideoEnabled));

      return currentState;
    }
  }

  @Override
  protected @NonNull WebRtcServiceState handleScreenSharingServiceReady(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleScreenSharingServiceReady()");

    LocalDeviceState localDeviceState      = currentState.getLocalDeviceState();
    Intent           mediaProjectionIntent = localDeviceState.getMediaProjectionIntent();

    if (localDeviceState.isScreenSharing()) {
      Log.w(tag, "handleScreenSharingServiceReady(): already screensharing!");
      return currentState;
    }

    if (mediaProjectionIntent == null) {
      Log.w(tag, "handleScreenSharingServiceReady(): Media intent is null, bailing");
      return currentState;
    }

    OutgoingVideoSourceRouter router = currentState.getVideoState().requireRouter();

    try {
      webRtcInteractor.getCallManager().setVideoEnable(true, true);
    } catch (CallException e) {
      return callFailure(currentState, "setVideoEnable() for screen share failed: ", e);
    }
    router.startScreenShare(mediaProjectionIntent);

    currentState = currentState.builder()
                               .changeLocalDeviceState()
                               .isScreenSharing(true)
                               .build();

    boolean remoteVideoEnabled = currentState.getCallInfoState().getRemoteCallParticipantsMap().values().stream().anyMatch(CallParticipant::isVideoEnabled);
    webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context, true, remoteVideoEnabled));
    WebRtcUtil.enableSpeakerPhoneIfNeeded(webRtcInteractor, currentState);

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    return activeCallDelegate.handleLocalHangup(currentState);
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
  protected @NonNull WebRtcServiceState handleReceivedOfferWhileActive(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleReceivedOfferWhileActive(currentState, remotePeer);
  }
}
