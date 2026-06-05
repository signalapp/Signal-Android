/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.WeakHashMap

/**
 * Applies temporary screenshot security for the given component lifecycle.
 *
 * Multiple callers can request security on the same window concurrently; the
 * flag is only cleared once every caller has released its hold.
 */
object TemporaryScreenshotSecurity {

  private val activeHolds = WeakHashMap<Window, Int>()

  @Composable
  fun bind() {
    val activity = LocalActivity.current as? ComponentActivity ?: return

    DisposableEffect(activity) {
      acquire(activity)

      onDispose {
        release(activity)
      }
    }
  }

  @JvmStatic
  fun bindToViewLifecycleOwner(fragment: Fragment) {
    val observer = LifecycleObserver { fragment.requireActivity() }

    fragment.viewLifecycleOwner.lifecycle.addObserver(observer)
  }

  @JvmStatic
  fun bind(activity: ComponentActivity) {
    val observer = LifecycleObserver { activity }

    activity.lifecycle.addObserver(observer)
  }

  private fun acquire(activity: ComponentActivity) {
    val window = activity.window
    val previous = activeHolds[window] ?: 0
    activeHolds[window] = previous + 1
    if (previous == 0 && !TextSecurePreferences.isScreenSecurityEnabled(activity)) {
      window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }

  private fun release(activity: ComponentActivity) {
    val window = activity.window
    val next = ((activeHolds[window] ?: 0) - 1).coerceAtLeast(0)
    if (next == 0) {
      activeHolds.remove(window)
      if (!TextSecurePreferences.isScreenSecurityEnabled(activity)) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      }
    } else {
      activeHolds[window] = next
    }
  }

  private class LifecycleObserver(
    private val activityProvider: () -> ComponentActivity
  ) : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
      acquire(activityProvider())
    }

    override fun onPause(owner: LifecycleOwner) {
      release(activityProvider())
    }
  }
}
