package org.thoughtcrime.securesms.crypto;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;

public final class ProfileKeyUtil {

  private ProfileKeyUtil() {
  }

  /**
   * @deprecated Will inline later as part of Versioned profiles.
   */
  @Deprecated
  public static @NonNull byte[] getProfileKey(@NonNull Context context) {
    byte[] profileKey = Recipient.self().getProfileKey();
    if (profileKey == null) {
      throw new AssertionError();
    }
    return profileKey;
  }
}
