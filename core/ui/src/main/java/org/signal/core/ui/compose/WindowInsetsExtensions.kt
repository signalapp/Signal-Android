/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.app.Activity
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The last API level that lays out edge-to-edge through the legacy system UI flags, and the last one
 * that hands a composition an empty inset while the bars are on screen.
 */
private const val LEGACY_INSET_SDK = 29

private const val STATUS_BAR_HEIGHT = "status_bar_height"
private const val NAVIGATION_BAR_HEIGHT = "navigation_bar_height"
private const val NAVIGATION_BAR_INTERACTION_MODE = "config_navBarInteractionMode"
private const val GESTURE_NAVIGATION_MODE = 2

private val NO_INSETS = WindowInsets(0, 0, 0, 0)

/**
 * [WindowInsets.statusBars], with the status bar that API <= 29 leaves out filled back in.
 * See [legacySystemBarInsets].
 */
val WindowInsets.Companion.statusBarsCompat: WindowInsets
  @Composable get() = statusBars.union(legacySystemBarInsets().only(WindowInsetsSides.Top))

/**
 * [WindowInsets.navigationBars], with the navigation bar that API <= 29 leaves out filled back in.
 * See [legacySystemBarInsets].
 */
val WindowInsets.Companion.navigationBarsCompat: WindowInsets
  @Composable get() = navigationBars.union(legacySystemBarInsets().only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))

/**
 * [WindowInsets.systemBars], with the bars that API <= 29 leaves out filled back in.
 * See [legacySystemBarInsets].
 */
val WindowInsets.Companion.systemBarsCompat: WindowInsets
  @Composable get() = systemBars.union(legacySystemBarInsets())

/**
 * [WindowInsets.safeDrawing], with the bars that API <= 29 leaves out filled back in.
 * See [legacySystemBarInsets].
 */
val WindowInsets.Companion.safeDrawingCompat: WindowInsets
  @Composable get() = safeDrawing.union(legacySystemBarInsets())

/**
 * What the system bars really take up, for the API levels where the insets that reach a composition
 * do not say.
 *
 * Every activity is laid out edge-to-edge, which on API <= 29 is a set of legacy system UI flags rather
 * than a window attribute. Content is then drawn behind the bars, but the insets dispatched down the
 * hierarchy routinely arrive empty anyway -- anything above the composition may consume them on the way,
 * and the flags going on do not by themselves force a fresh dispatch. Compose believes the window it is
 * handed, so the bars end up covering whatever is laid out against them. The view hierarchy has long
 * worked around this in `InsetAwareConstraintLayout` and `ViewUtil`; this is the same measurement for
 * the composition.
 *
 * Measured from the window itself rather than from the dispatch, falling back to the platform's own bar
 * dimensions when even that comes up empty. A bar that is hidden -- immersive mode, or a window that is
 * not laid out behind it -- contributes nothing, so this only ever restores a bar that is really there.
 * On API 30+ it is always empty and the stock insets stand on their own.
 *
 * The resource fallback is the narrow one: portrait, full window, three button bar. Nothing in the
 * platform's dimensions says which edge a landscape bar sits on or whether this window is even up
 * against one, and the window measurement above it already covers those.
 */
@Composable
private fun legacySystemBarInsets(): WindowInsets {
  if (Build.VERSION.SDK_INT > LEGACY_INSET_SDK) {
    return NO_INSETS
  }

  val view = LocalView.current
  val density = LocalDensity.current
  val configuration = LocalConfiguration.current

  // Snapshot reads of the dispatched insets, so bars that do eventually arrive re-measure rather than
  // leaving a stale fallback standing.
  val dispatchedTop = WindowInsets.systemBars.getTop(density)
  val dispatchedBottom = WindowInsets.systemBars.getBottom(density)

  return remember(view, configuration, dispatchedTop, dispatchedBottom) {
    val insets = measureSystemBars(view)
    WindowInsets(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
  }
}

@Suppress("DEPRECATION")
private fun measureSystemBars(view: View): Insets {
  val systemUiVisibility = view.windowSystemUiVisibility
  val behindStatusBar = systemUiVisibility.hasFlag(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) && !systemUiVisibility.hasFlag(View.SYSTEM_UI_FLAG_FULLSCREEN)
  val behindNavigationBar = systemUiVisibility.hasFlag(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) && !systemUiVisibility.hasFlag(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

  if (!behindStatusBar && !behindNavigationBar) {
    return Insets.NONE
  }

  val stableInsets = ViewCompat.getRootWindowInsets(view)?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()) ?: Insets.NONE
  if (stableInsets != Insets.NONE) {
    return Insets.of(
      if (behindNavigationBar) stableInsets.left else 0,
      if (behindStatusBar) stableInsets.top else 0,
      if (behindNavigationBar) stableInsets.right else 0,
      if (behindNavigationBar) stableInsets.bottom else 0
    )
  }

  if (view.isInMultiWindowMode()) {
    return Insets.NONE
  }

  val resources = view.resources
  val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  return Insets.of(
    0,
    if (behindStatusBar) resources.systemDimensionPixelSize(STATUS_BAR_HEIGHT) else 0,
    0,
    if (behindNavigationBar && isPortrait && !resources.isGestureNavigation()) resources.systemDimensionPixelSize(NAVIGATION_BAR_HEIGHT) else 0
  )
}

/**
 * A window sharing the display holds no bar of its own to measure, so the whole-display dimensions
 * the fallback reads would be space it invents.
 */
private fun View.isInMultiWindowMode(): Boolean {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
    return false
  }

  var candidate = context
  while (candidate is ContextWrapper) {
    if (candidate is Activity) {
      return candidate.isInMultiWindowMode
    }
    candidate = candidate.baseContext
  }

  return false
}

private fun Int.hasFlag(flag: Int): Boolean = this and flag != 0

private fun Resources.systemDimensionPixelSize(name: String): Int {
  val id = getIdentifier(name, "dimen", "android")
  return if (id > 0) getDimensionPixelSize(id) else 0
}

/**
 * Whether the device is driven by gestures rather than by a three button bar, in which case an empty
 * bottom inset is the truth and the [NAVIGATION_BAR_HEIGHT] fallback would invent space.
 */
private fun Resources.isGestureNavigation(): Boolean {
  val id = getIdentifier(NAVIGATION_BAR_INTERACTION_MODE, "integer", "android")
  if (id <= 0) {
    return false
  }

  return try {
    getInteger(id) == GESTURE_NAVIGATION_MODE
  } catch (ignored: Resources.NotFoundException) {
    false
  }
}
