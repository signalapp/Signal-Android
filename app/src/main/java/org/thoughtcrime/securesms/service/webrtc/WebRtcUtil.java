package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;
import android.media.AudioManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.GroupCall;
import org.signal.ringrtc.PeekInfo;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;

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
    AudioManager audioManager = ServiceUtil.getAudioManager(context);
    if (audioManager.isSpeakerphoneOn() || audioManager.isBluetoothScoOn() || audioManager.isWiredHeadsetOn()) {
      return LockManager.PhoneState.IN_HANDS_FREE_CALL;
    } else {
      return LockManager.PhoneState.IN_CALL;
    }
  }

  public static @NonNull CallManager.CallMediaType getCallMediaTypeFromOfferType(@NonNull OfferMessage.Type offerType) {
    return offerType == OfferMessage.Type.VIDEO_CALL ? CallManager.CallMediaType.VIDEO_CALL : CallManager.CallMediaType.AUDIO_CALL;
  }

  public static void enableSpeakerPhoneIfNeeded(@NonNull Context context, boolean enable) {
    if (!enable) {
      return;
    }

    AudioManager androidAudioManager = ServiceUtil.getAudioManager(context);
    //noinspection deprecation
    boolean      shouldEnable        = !(androidAudioManager.isSpeakerphoneOn() || androidAudioManager.isBluetoothScoOn() || androidAudioManager.isWiredHeadsetOn());

    if (shouldEnable) {
      androidAudioManager.setSpeakerphoneOn(true);
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
