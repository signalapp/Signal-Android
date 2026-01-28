package org.thoughtcrime.securesms.util

import android.content.Context
import android.provider.Settings

object AccessibilityUtil {
  @JvmStatic
  fun areAnimationsDisabled(context: Context): Boolean {
    return Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
  }
}
