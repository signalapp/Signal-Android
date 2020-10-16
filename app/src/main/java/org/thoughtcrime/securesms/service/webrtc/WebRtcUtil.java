package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;
import android.media.AudioManager;

import androidx.annotation.NonNull;

import org.signal.ringrtc.CallManager;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.DjbECPublicKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;

/**
 * Calling specific helpers.
 */
public final class WebRtcUtil {

  private WebRtcUtil() {}

  public static @NonNull byte[] getPublicKeyBytes(@NonNull byte[] identityKey) throws InvalidKeyException {
    ECPublicKey key = Curve.decodePoint(identityKey, 0);

    if (key instanceof DjbECPublicKey) {
      return ((DjbECPublicKey) key).getPublicKey();
    }
    throw new InvalidKeyException();
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
}
