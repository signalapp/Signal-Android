package org.thoughtcrime.securesms.service.webrtc;

import android.media.AudioManager;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.ServiceUtil;

/**
 * Encapsulates the shared logic to deal with local device actions. Other action processors inherit
 * the behavior by extending it instead of delegating. It is not intended to be the main processor
 * for the system.
 */
public abstract class DeviceAwareActionProcessor extends WebRtcActionProcessor {

  public DeviceAwareActionProcessor(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    super(webRtcInteractor, tag);
  }

  @Override
  protected @NonNull WebRtcServiceState handleWiredHeadsetChange(@NonNull WebRtcServiceState currentState, boolean present) {
    Log.i(tag, "handleWiredHeadsetChange():");

    AudioManager androidAudioManager = ServiceUtil.getAudioManager(context);

    if (present && androidAudioManager.isSpeakerphoneOn()) {
      androidAudioManager.setSpeakerphoneOn(false);
      androidAudioManager.setBluetoothScoOn(false);
    } else if (!present && !androidAudioManager.isSpeakerphoneOn() && !androidAudioManager.isBluetoothScoOn() && currentState.getLocalDeviceState().getCameraState().isEnabled()) {
      androidAudioManager.setSpeakerphoneOn(true);
    }

    webRtcInteractor.postStateUpdate(currentState);

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleBluetoothChange(@NonNull WebRtcServiceState currentState, boolean available) {
    Log.i(tag, "handleBluetoothChange(): " + available);

    if (available && currentState.getLocalDeviceState().wantsBluetooth()) {
      webRtcInteractor.setWantsBluetoothConnection(true);
    }

    return currentState.builder()
                       .changeLocalDeviceState()
                       .isBluetoothAvailable(available)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetSpeakerAudio(@NonNull WebRtcServiceState currentState, boolean isSpeaker) {
    Log.i(tag, "handleSetSpeakerAudio(): " + isSpeaker);

    AudioManager androidAudioManager = ServiceUtil.getAudioManager(context);

    webRtcInteractor.setWantsBluetoothConnection(false);
    androidAudioManager.setSpeakerphoneOn(isSpeaker);

    if (!currentState.getLocalDeviceState().getCameraState().isEnabled()) {
      webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context));
    }

    webRtcInteractor.postStateUpdate(currentState);

    return currentState.builder()
                       .changeLocalDeviceState()
                       .wantsBluetooth(false)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetBluetoothAudio(@NonNull WebRtcServiceState currentState, boolean isBluetooth) {
    Log.i(tag, "handleSetBluetoothAudio(): " + isBluetooth);

    webRtcInteractor.setWantsBluetoothConnection(isBluetooth);

    if (!currentState.getLocalDeviceState().getCameraState().isEnabled()) {
      webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context));
    }

    webRtcInteractor.postStateUpdate(currentState);

    return currentState.builder()
                       .changeLocalDeviceState()
                       .wantsBluetooth(isBluetooth)
                       .build();
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetCameraFlip(@NonNull WebRtcServiceState currentState) {
    Log.i(tag, "handleSetCameraFlip():");

    if (currentState.getLocalDeviceState().getCameraState().isEnabled() && currentState.getVideoState().getCamera() != null) {
      currentState.getVideoState().getCamera().flip();
      return currentState.builder()
                         .changeLocalDeviceState()
                         .cameraState(currentState.getVideoState().getCamera().getCameraState())
                         .build();
    }
    return currentState;
  }

  @Override
  public @NonNull WebRtcServiceState handleCameraSwitchCompleted(@NonNull WebRtcServiceState currentState, @NonNull CameraState newCameraState) {
    Log.i(tag, "handleCameraSwitchCompleted():");

    BroadcastVideoSink localSink = currentState.getVideoState().getLocalSink();
    if (localSink != null) {
      localSink.setRotateToRightSide(newCameraState.getActiveDirection() == CameraState.Direction.BACK);
    }

    return currentState.builder()
                       .changeLocalDeviceState()
                       .cameraState(newCameraState)
                       .build();
  }
}
