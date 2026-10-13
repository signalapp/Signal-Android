/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Keeps the screen on while this composable is in the composition by toggling
 * [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON] on the host activity window.
 *
 * @param enabled When false, the flag is left alone, letting callers scope the effect to a piece of state without
 *   having to conditionally compose it.
 */
@Composable
fun KeepScreenOnEffect(enabled: Boolean = true) {
  val activity = LocalActivity.current

  DisposableEffect(activity, enabled) {
    val window = if (enabled) activity?.window else null
    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    onDispose {
      window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }
}
