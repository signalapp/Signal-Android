/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.Activity
import android.os.Build
import androidx.annotation.AnimRes

val Activity.OVERRIDE_TRANSITION_OPEN_COMPAT: Int get() = 0
val Activity.OVERRIDE_TRANSITION_CLOSE_COMPAT: Int get() = 1

fun Activity.overrideActivityTransitionCompat(overrideType: Int, @AnimRes enterAnim: Int, @AnimRes exitAnim: Int) {
  if (Build.VERSION.SDK_INT >= 34) {
    overrideActivityTransition(overrideType, enterAnim, exitAnim)
  } else {
    @Suppress("DEPRECATION")
    overridePendingTransition(enterAnim, exitAnim)
  }
}
