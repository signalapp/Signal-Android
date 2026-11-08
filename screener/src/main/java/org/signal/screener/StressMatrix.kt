/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection

private const val DEVICE = 0
private const val DISPLAY_SIZE = 1
private const val FONT_SCALE = 2
private const val LAYOUT_DIRECTION = 3
private const val LANGUAGE = 4

/**
 * The dimensions a layout should pass its checks under, combined into [StressConfig]s.
 *
 * [displaySizes] are the system "Display size" setting's scale factors, which shrink a device's window in dp.
 * The default [languages] are generated from the English strings, so they don't change when translations land.
 * The first value of each dimension is its everyday default, and a null language is English.
 */
data class StressMatrix(
  val devices: Map<String, DpSize> = mapOf(
    "phone" to StressConfig.WindowSizes.Phone,
    "landscape" to StressConfig.WindowSizes.PhoneLandscape,
    "tablet" to StressConfig.WindowSizes.TabletLandscape
  ),
  val displaySizes: List<Float> = listOf(1f, LARGEST_DISPLAY_SIZE),
  val fontScales: List<Float> = listOf(1f, 2f),
  val layoutDirections: List<LayoutDirection> = listOf(LayoutDirection.Ltr, LayoutDirection.Rtl),
  val languages: List<StressLanguage?> = listOf(null, PseudoLanguage.EXPANSIVE, PseudoLanguage.COMPOUND)
) {

  companion object {
    /** Roughly the largest "Display size" setting on a typical phone. */
    const val LARGEST_DISPLAY_SIZE = 1.3f
  }

  private val dimensions: List<Int>
    get() = listOf(devices.size, displaySizes.size, fontScales.size, layoutDirections.size, languages.size)

  /** Every combination of every dimension. */
  fun exhaustive(): List<StressConfig> {
    return allCombinations().map { it.toConfig() }
  }

  /**
   * The fewest configs, chosen greedily, in which every pair of values from two different dimensions appears
   * together at least once. Always starts with the everyday default and the same at the largest font scale,
   * which [LayoutCheck.TEXT_DOES_NOT_SCALE] compares.
   */
  fun pairwise(): List<StressConfig> {
    val candidates = allCombinations()
    val uncovered = candidates.flatMapTo(mutableSetOf()) { it.pairs() }

    val defaults = List(dimensions.size) { 0 }
    val largestFont = defaults.toMutableList().apply { this[FONT_SCALE] = fontScales.lastIndex }

    val chosen = mutableListOf(defaults)
    if (largestFont != defaults) {
      chosen += largestFont
    }
    chosen.forEach { uncovered -= it.pairs() }

    while (uncovered.isNotEmpty()) {
      val next = candidates.maxBy { candidate -> candidate.pairs().count { it in uncovered } }
      chosen += next
      uncovered -= next.pairs()
    }

    return chosen.map { it.toConfig() }
  }

  private fun allCombinations(): List<List<Int>> {
    return dimensions.fold(listOf(emptyList())) { combinations, size ->
      combinations.flatMap { combination -> (0 until size).map { combination + it } }
    }
  }

  private fun List<Int>.pairs(): Set<List<Int>> {
    val values = this
    return buildSet {
      for (a in values.indices) {
        for (b in a + 1 until values.size) {
          add(listOf(a, values[a], b, values[b]))
        }
      }
    }
  }

  private fun List<Int>.toConfig(): StressConfig {
    val (deviceName, deviceSize) = devices.entries.elementAt(this[DEVICE]).toPair()
    val displaySize = displaySizes[this[DISPLAY_SIZE]]
    val fontScale = fontScales[this[FONT_SCALE]]
    val layoutDirection = layoutDirections[this[LAYOUT_DIRECTION]]
    val language = languages[this[LANGUAGE]]

    val name = buildList {
      add(deviceName)
      if (displaySize != 1f) add("display ${StressConfig.formatScale(displaySize)}x")
      if (fontScale != 1f) add("font ${StressConfig.formatScale(fontScale)}x")
      if (layoutDirection == LayoutDirection.Rtl) add("RTL")
      if (language != null) add(language.label)
    }.joinToString(" + ")

    return StressConfig(
      name = name,
      windowSize = StressConfig.WindowSizes.atDisplaySize(deviceSize, displaySize),
      fontScale = fontScale,
      layoutDirection = layoutDirection,
      locale = language?.locale
    )
  }
}
