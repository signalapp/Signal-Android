/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.util.WeakHashMap

private val holdersByWindow = WeakHashMap<Window, Int>()

/**
 * Keeps the screen on while this composable is in the composition by toggling
 * [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON] on the host activity window.
 *
 * Holders are counted per window, so nested effects (e.g. a whole flow and a screen within it) don't clear the flag
 * out from under each other.
 *
 * @param enabled When false, the flag is left alone, letting callers scope the effect to a piece of state without
 *   having to conditionally compose it.
 */
@Composable
fun KeepScreenOnEffect(enabled: Boolean = true) {
  val activity = LocalActivity.current

  DisposableEffect(activity, enabled) {
    val window = if (enabled) activity?.window else null
    window?.let(::acquire)

    onDispose {
      window?.let(::release)
    }
  }
}

private fun acquire(window: Window) {
  val holders = (holdersByWindow[window] ?: 0) + 1
  holdersByWindow[window] = holders

  if (holders == 1) {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }
}

private fun release(window: Window) {
  val holders = (holdersByWindow[window] ?: 0) - 1

  if (holders <= 0) {
    holdersByWindow.remove(window)
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  } else {
    holdersByWindow[window] = holders
  }
}
