package org.thoughtcrime.securesms.service.webrtc;

import android.os.ResultReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.thoughtcrime.securesms.ringrtc.IceCandidateParcel;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;

import java.util.ArrayList;

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
      webRtcInteractor.getCallManager().setVideoEnable(enable);
    } catch  (CallException e) {
      return callFailure(currentState, "setVideoEnable() failed: ", e);
    }

    currentState = currentState.builder()
                               .changeLocalDeviceState()
                               .cameraState(currentState.getVideoState().requireCamera().getCameraState())
                               .build();

    if (currentState.getLocalDeviceState().getCameraState().isEnabled()) {
      webRtcInteractor.updatePhoneState(LockManager.PhoneState.IN_VIDEO);
    } else {
      webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context));
    }

    WebRtcUtil.enableSpeakerPhoneIfNeeded(context, currentState.getLocalDeviceState().getCameraState().isEnabled());

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
  protected @NonNull  WebRtcServiceState handleRemoteVideoEnable(@NonNull WebRtcServiceState currentState, boolean enable) {
    return activeCallDelegate.handleRemoteVideoEnable(currentState, enable);
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
  protected @NonNull WebRtcServiceState handleLocalHangup(@NonNull WebRtcServiceState currentState) {
    return activeCallDelegate.handleLocalHangup(currentState);
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
  protected @NonNull WebRtcServiceState handleReceivedOfferWhileActive(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    return activeCallDelegate.handleReceivedOfferWhileActive(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleCallConcluded(@NonNull WebRtcServiceState currentState, @Nullable RemotePeer remotePeer) {
    return activeCallDelegate.handleCallConcluded(currentState, remotePeer);
  }
}
