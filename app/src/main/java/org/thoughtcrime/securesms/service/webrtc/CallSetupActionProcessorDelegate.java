package org.thoughtcrime.securesms.service.webrtc;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;

import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_ESTABLISHED;

/**
 * Encapsulates the shared logic to setup a 1:1 call. Setup primarily includes retrieving turn servers and
 * transitioning to the connected state. Other action processors delegate the appropriate action to it but it is
 * not intended to be the main processor for the system.
 */
public class CallSetupActionProcessorDelegate extends WebRtcActionProcessor {

  public CallSetupActionProcessorDelegate(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    super(webRtcInteractor, tag);
  }

  @Override
  public @NonNull WebRtcServiceState handleCallConnected(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    if (!remotePeer.callIdEquals(currentState.getCallInfoState().getActivePeer())) {
      Log.w(tag, "handleCallConnected(): Ignoring for inactive call.");
      return currentState;
    }

    Log.i(tag, "handleCallConnected(): call_id: " + remotePeer.getCallId());

    RemotePeer activePeer = currentState.getCallInfoState().requireActivePeer();

    webRtcInteractor.sendAcceptedCallEventSyncMessage(
      activePeer,
      currentState.getCallInfoState().getCallState() == WebRtcViewModel.State.CALL_RINGING,
      currentState.getCallSetupState(activePeer).isAcceptWithVideo() || currentState.getLocalDeviceState().getCameraState().isEnabled()
    );

    AppDependencies.getAppForegroundObserver().removeListener(webRtcInteractor.getForegroundListener());
    webRtcInteractor.startAudioCommunication();
    webRtcInteractor.activateCall(activePeer.getId());

    activePeer.connected();

    if (currentState.getLocalDeviceState().getCameraState().isEnabled()) {
      webRtcInteractor.updatePhoneState(LockManager.PhoneState.IN_VIDEO);
    } else {
      webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context));
    }

    currentState = currentState.builder()
                               .actionProcessor(new ConnectedCallActionProcessor(webRtcInteractor))
                               .changeCallInfoState()
                               .callState(WebRtcViewModel.State.CALL_CONNECTED)
                               .callConnectedTime(System.currentTimeMillis())
                               .commit()
                               .changeLocalDeviceState()
                               .build();

    boolean isRemoteVideoOffer = currentState.getCallSetupState(activePeer).isRemoteVideoOffer();

    webRtcInteractor.setCallInProgressNotification(TYPE_ESTABLISHED, activePeer, isRemoteVideoOffer);
    webRtcInteractor.unregisterPowerButtonReceiver();

    try {
      CallManager callManager = webRtcInteractor.getCallManager();
      callManager.setAudioEnable(currentState.getLocalDeviceState().isMicrophoneEnabled());
      callManager.setVideoEnable(currentState.getLocalDeviceState().getCameraState().isEnabled());
    } catch (CallException e) {
      return callFailure(currentState, "Enabling audio/video failed: ", e);
    }

    if (currentState.getCallSetupState(activePeer).isAcceptWithVideo()) {
      currentState = currentState.getActionProcessor().handleSetEnableVideo(currentState, true);
    }

    if (currentState.getCallSetupState(activePeer).isAcceptWithVideo() || currentState.getLocalDeviceState().getCameraState().isEnabled()) {
      webRtcInteractor.setDefaultAudioDevice(activePeer.getId(), SignalAudioManager.AudioDevice.SPEAKER_PHONE, false);
    } else {
      webRtcInteractor.setDefaultAudioDevice(activePeer.getId(), SignalAudioManager.AudioDevice.EARPIECE, false);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(tag, "handleSetEnableVideo(): enable: " + enable);

    Camera camera = currentState.getVideoState().requireCamera();

    if (camera.isInitialized()) {
      camera.setEnabled(enable);
    }

    currentState = currentState.builder()
                               .changeLocalDeviceState()
                               .cameraState(camera.getCameraState())
                               .build();

    //noinspection SimplifiableBooleanExpression
    if ((enable && camera.isInitialized()) || !enable) {
      try {
        CallManager callManager = webRtcInteractor.getCallManager();
        callManager.setVideoEnable(enable);
      } catch (CallException e) {
        Log.w(tag, "Unable change video enabled state to " + enable, e);
      }
    }

    WebRtcUtil.enableSpeakerPhoneIfNeeded(webRtcInteractor, currentState);

    return currentState;
  }
}
