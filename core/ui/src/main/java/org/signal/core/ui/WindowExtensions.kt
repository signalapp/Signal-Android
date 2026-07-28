/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import android.graphics.Color
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import org.signal.core.ui.util.ThemeUtil

private val DARK_NAVIGATION_BAR_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
private val LIGHT_NAVIGATION_BAR_SCRIM = Color.argb(0xe6, 0xff, 0xff, 0xff)

/**
 * Initializes screenshot security on the window based on user preferences.
 */
fun Window.initializeScreenshotSecurity() {
  if (CoreUiDependencies.isScreenSecurityEnabled) {
    addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  } else {
    clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
  }
}

/**
 * Dialog-window analog of [androidx.activity.enableEdgeToEdge]: lays the window out edge-to-edge with
 * transparent (or scrimmed, pre-29) system bars, matching what the framework enforces on API 35+. Dialog
 * windows are not covered by the activity-level call in BaseActivity, so every non-floating dialog must opt
 * in itself.
 *
 * Bar icon appearance (`windowLightStatusBar` / `windowLightNavigationBar`) is left to the window's theme,
 * which stays honored under edge-to-edge.
 */
@Suppress("DEPRECATION")
fun Window.enableEdgeToEdge() {
  WindowCompat.setDecorFitsSystemWindows(this, false)

  if (Build.VERSION.SDK_INT >= 35) {
    // Edge-to-edge is enforced, which forces the bars transparent and makes both setters no-ops.
    return
  }

  statusBarColor = Color.TRANSPARENT
  navigationBarColor = when {
    Build.VERSION.SDK_INT >= 29 -> Color.TRANSPARENT
    Build.VERSION.SDK_INT >= 27 && ThemeUtil.getThemedBoolean(context, android.R.attr.windowLightNavigationBar) -> LIGHT_NAVIGATION_BAR_SCRIM
    else -> DARK_NAVIGATION_BAR_SCRIM
  }
}
