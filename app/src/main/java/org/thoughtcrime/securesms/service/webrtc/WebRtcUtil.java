package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.GroupCall;
import org.signal.ringrtc.PeekInfo;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;

/**
 * Calling specific helpers.
 */
public final class WebRtcUtil {

  private WebRtcUtil() {}

  public static @NonNull byte[] getPublicKeyBytes(@NonNull byte[] identityKey) throws InvalidKeyException {
    ECPublicKey key = Curve.decodePoint(identityKey, 0);
    return key.getPublicKeyBytes();
  }

  public static @NonNull LockManager.PhoneState getInCallPhoneState(@NonNull Context context) {
    AudioManagerCompat audioManager = AppDependencies.getAndroidCallAudioManager();
    if (audioManager.isSpeakerphoneOn() || audioManager.isBluetoothConnected() || audioManager.isWiredHeadsetOn()) {
      return LockManager.PhoneState.IN_HANDS_FREE_CALL;
    } else {
      return LockManager.PhoneState.IN_CALL;
    }
  }

  public static @NonNull CallManager.CallMediaType getCallMediaTypeFromOfferType(@NonNull OfferMessage.Type offerType) {
    return offerType == OfferMessage.Type.VIDEO_CALL ? CallManager.CallMediaType.VIDEO_CALL : CallManager.CallMediaType.AUDIO_CALL;
  }

  public static @NonNull OfferMessage.Type getOfferTypeFromCallMediaType(@Nullable CallManager.CallMediaType callMediaType) {
    return callMediaType == CallManager.CallMediaType.VIDEO_CALL ? OfferMessage.Type.VIDEO_CALL : OfferMessage.Type.AUDIO_CALL;
  }

  public static @NonNull HangupMessage.Type getHangupTypeFromCallHangupType(@NonNull CallManager.HangupType hangupType) {
    switch (hangupType) {
      case ACCEPTED:
        return HangupMessage.Type.ACCEPTED;
      case BUSY:
        return HangupMessage.Type.BUSY;
      case NORMAL:
        return HangupMessage.Type.NORMAL;
      case DECLINED:
        return HangupMessage.Type.DECLINED;
      case NEED_PERMISSION:
        return HangupMessage.Type.NEED_PERMISSION;
      default:
        throw new IllegalArgumentException("Unexpected hangup type: " + hangupType);
    }
  }

  public static OpaqueMessage.Urgency getUrgencyFromCallUrgency(@NonNull CallManager.CallMessageUrgency urgency) {
    if (urgency == CallManager.CallMessageUrgency.HANDLE_IMMEDIATELY) {
      return OpaqueMessage.Urgency.HANDLE_IMMEDIATELY;
    }
    return OpaqueMessage.Urgency.DROPPABLE;
  }

  public static void enableSpeakerPhoneIfNeeded(@NonNull WebRtcInteractor webRtcInteractor, WebRtcServiceState currentState) {
    if (!currentState.getLocalDeviceState().getCameraState().isEnabled()) {
      return;
    }

    if (currentState.getLocalDeviceState().getActiveDevice() == SignalAudioManager.AudioDevice.EARPIECE ||
        currentState.getLocalDeviceState().getActiveDevice() == SignalAudioManager.AudioDevice.NONE &&
        currentState.getCallInfoState().getActivePeer() != null)
    {
      webRtcInteractor.setDefaultAudioDevice(currentState.getCallInfoState().requireActivePeer().getId(), SignalAudioManager.AudioDevice.SPEAKER_PHONE, true);
    }
  }

  public static @NonNull WebRtcViewModel.GroupCallState groupCallStateForConnection(@NonNull GroupCall.ConnectionState connectionState) {
    switch (connectionState) {
      case CONNECTING:
        return WebRtcViewModel.GroupCallState.CONNECTING;
      case CONNECTED:
        return WebRtcViewModel.GroupCallState.CONNECTED;
      case RECONNECTING:
        return WebRtcViewModel.GroupCallState.RECONNECTING;
      default:
        return WebRtcViewModel.GroupCallState.DISCONNECTED;
    }
  }

  public static @Nullable String getGroupCallEraId(@Nullable GroupCall groupCall) {
    if (groupCall == null) {
      return null;
    }

    PeekInfo peekInfo = groupCall.getPeekInfo();
    return peekInfo != null ? peekInfo.getEraId() : null;
  }

  public static boolean isCallFull(@Nullable PeekInfo peekInfo) {
    return peekInfo != null && peekInfo.getMaxDevices() != null && peekInfo.getDeviceCount() >= peekInfo.getMaxDevices();
  }
}
