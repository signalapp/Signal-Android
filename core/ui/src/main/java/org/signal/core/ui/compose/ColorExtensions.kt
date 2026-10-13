/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * This color's lightness in HSL space, where 0 is black and 1 is white.
 */
val Color.hslLightness: Float
  get() {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)

    return (max + min) / 2f
  }

/**
 * Multiplies this color's HSL saturation by [fraction], leaving hue, lightness, and alpha untouched. A [fraction] below
 * 1 washes the color out towards gray without changing how light or dark it reads.
 *
 * Unlike interpolating towards a gray in RGB space, this shifts every input by the same proportion, so a color that
 * already starts out pale is muted just as visibly as a vivid one.
 */
fun Color.scaleSaturation(fraction: Float): Color {
  val hsl = toHsl()

  return Color.hsl(
    hue = hsl[0],
    saturation = (hsl[1] * fraction).coerceIn(0f, 1f),
    lightness = hsl[2],
    alpha = alpha
  )
}

/**
 * Moves this color [fraction] of the way to [other]'s lightness, leaving hue, saturation, and alpha untouched. The
 * result keeps its own hue, so it stays recognizable while being pulled towards how light or dark [other] is.
 */
fun Color.shiftLightnessToward(other: Color, fraction: Float): Color {
  val hsl = toHsl()
  val target = other.hslLightness

  return Color.hsl(
    hue = hsl[0],
    saturation = hsl[1],
    lightness = (hsl[2] + (target - hsl[2]) * fraction).coerceIn(0f, 1f),
    alpha = alpha
  )
}

/**
 * This color as `[hue, saturation, lightness]`, matching the ranges [Color.hsl] expects. Alpha is not included.
 */
private fun Color.toHsl(): FloatArray {
  val max = maxOf(red, green, blue)
  val min = minOf(red, green, blue)
  val delta = max - min
  val lightness = (max + min) / 2f

  if (delta == 0f) {
    return floatArrayOf(0f, 0f, lightness)
  }

  val hue = when (max) {
    red -> 60f * (((green - blue) / delta) % 6f)
    green -> 60f * (((blue - red) / delta) + 2f)
    else -> 60f * (((red - green) / delta) + 4f)
  }

  return floatArrayOf(
    if (hue < 0f) hue + 360f else hue,
    delta / (1f - abs(2f * lightness - 1f)),
    lightness
  )
}
