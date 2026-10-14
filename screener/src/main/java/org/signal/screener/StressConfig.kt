/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.LayoutDirection
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DeviceSizes
import kotlin.math.roundToInt

/**
 * A device configuration that a layout is expected to pass its checks under, e.g. the largest system font scale.
 *
 * [windowSize] also sets the configuration and window info that window size classes read. It must fit inside the
 * test's real window: see [ROBOLECTRIC_QUALIFIERS].
 *
 * [locale] is a BCP-47 tag such as "de". It only changes text that comes from string resources.
 */
data class StressConfig(
  val name: String,
  val windowSize: DpSize = WindowSizes.Phone,
  val fontScale: Float = 1f,
  val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
  val locale: String? = null
) {
  object WindowSizes {
    val Phone = DpSize(DeviceSizes.PHONE_WIDTH.dp, DeviceSizes.PHONE_HEIGHT.dp)

    /** [Phone] at the largest system display size. */
    val SmallPhone = atDisplaySize(Phone, StressMatrix.LARGEST_DISPLAY_SIZE)
    val PhoneLandscape = DpSize(DeviceSizes.PHONE_HEIGHT.dp, DeviceSizes.PHONE_WIDTH.dp)
    val TabletLandscape = DpSize(DeviceSizes.TABLET_HEIGHT.dp, DeviceSizes.TABLET_WIDTH.dp)

    /** [windowSize] shrunk by the system "Display size" setting, in whole dp like a real window. */
    internal fun atDisplaySize(windowSize: DpSize, displaySize: Float): DpSize {
      return DpSize((windowSize.width.value / displaySize).roundToInt().dp, (windowSize.height.value / displaySize).roundToInt().dp)
    }
  }

  companion object {
    /**
     * Robolectric qualifiers for a window that fits every [StressMatrix] config. Compose renders a larger requested window
     * at a lower density, and the text layout it exposes for inspection ignores that density, so all text would
     * look cut off.
     */
    const val ROBOLECTRIC_QUALIFIERS = "w1280dp-h800dp"

    val Baseline = StressConfig(name = "baseline")
    val LargestFont = StressConfig(name = "font 2x", fontScale = 2f)
    val LargestFontLandscape = StressConfig(name = "font 2x + landscape", windowSize = WindowSizes.PhoneLandscape, fontScale = 2f)
    val LargestFontTablet = StressConfig(name = "font 2x + tablet", windowSize = WindowSizes.TabletLandscape, fontScale = 2f)

    val Default: List<StressConfig> = StressMatrix().pairwise()

    /** Formats a scale factor like "2" or "1.3". */
    internal fun formatScale(scale: Float): String = if (scale % 1f == 0f) scale.toInt().toString() else scale.toString()
  }

  internal val override: DeviceConfigurationOverride
    get() {
      var override = DeviceConfigurationOverride.WindowSize(windowSize) then
        DeviceConfigurationOverride.FontScale(fontScale) then
        DeviceConfigurationOverride.LayoutDirection(layoutDirection)

      if (locale != null) {
        override = override then DeviceConfigurationOverride.Locales(LocaleList(locale))
      }

      // Last, so it wraps the context the locale override creates
      return override then displayMetricsMatchingWindow()
    }

  /**
   * `@Preview` has no layout direction, so RTL is expressed the way `@RtlPreview` does it: as the locale written in
   * Arabic script.
   */
  internal fun toPreviewAnnotation(): String {
    val args = mutableListOf(
      "name = \"$name\"",
      "device = \"spec:width=${windowSize.width.value.toInt()}dp,height=${windowSize.height.value.toInt()}dp\""
    )

    if (fontScale != 1f) {
      args += "fontScale = ${formatScale(fontScale)}f"
    }

    val previewLocale = previewLocale()
    if (previewLocale != null) {
      args += "locale = \"$previewLocale\""
    }

    return "@Preview(${args.joinToString()})"
  }

  private fun previewLocale(): String? {
    val parts = (locale ?: if (layoutDirection == LayoutDirection.Rtl) "en" else return null).split("-")
    val subtags = if (layoutDirection == LayoutDirection.Rtl) listOf(parts.first(), "Arab") + parts.drop(1) else parts
    return if (subtags.size == 1) subtags.single() else "b+" + subtags.joinToString("+")
  }
}
