/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.DeviceConfigurationOverride
import kotlin.math.roundToInt

/**
 * Gives content [Resources] whose display metrics match the overridden window. [DeviceConfigurationOverride.WindowSize]
 * only overrides the configuration and window info, so code that sizes itself from display metrics would otherwise
 * still see the test's real window.
 */
internal fun displayMetricsMatchingWindow(): DeviceConfigurationOverride {
  return DeviceConfigurationOverride { content ->
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val windowContext = remember(context, configuration) { WindowContext(context, configuration) }

    CompositionLocalProvider(
      LocalContext provides windowContext,
      LocalResources provides windowContext.resources
    ) {
      content()
    }
  }
}

private class WindowContext(base: Context, configuration: Configuration) : ContextWrapper(base) {
  private val windowResources = WindowResources(base.resources, configuration)

  override fun getResources(): Resources = windowResources
}

/**
 * Overrides [getDisplayMetrics] because the constructor recomputes the window dimensions from the real display.
 */
@Suppress("DEPRECATION")
private class WindowResources(base: Resources, configuration: Configuration) : Resources(base.assets, base.displayMetrics, configuration) {
  private val windowMetrics = DisplayMetrics().apply {
    setTo(base.displayMetrics)
    widthPixels = (configuration.screenWidthDp * density).roundToInt()
    heightPixels = (configuration.screenHeightDp * density).roundToInt()
  }

  override fun getDisplayMetrics(): DisplayMetrics = windowMetrics
}
