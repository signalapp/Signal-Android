package org.thoughtcrime.securesms.lock.v2;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.CensorshipUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public final class PinUtil {

  private PinUtil() {}

  public static boolean userHasPin(@NonNull Context context) {
    return TextSecurePreferences.isV1RegistrationLockEnabled(context) || SignalStore.kbsValues().isV2RegistrationLockEnabled();
  }

  public static boolean shouldShowPinCreationDuringRegistration(@NonNull Context context) {
    return FeatureFlags.pinsForAll() && !PinUtil.userHasPin(context);
  }
}
