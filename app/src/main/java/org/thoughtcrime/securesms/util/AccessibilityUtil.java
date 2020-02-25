package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.provider.Settings;

public final class AccessibilityUtil {

  private AccessibilityUtil() {
  }

  public static boolean areAnimationsDisabled(Context context) {
    return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1) == 0f;
  }
}
