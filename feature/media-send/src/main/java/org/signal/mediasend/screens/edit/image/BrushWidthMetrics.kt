/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/**
 * Dimensions and animation values shared by [BrushWidthBar] and [BrushWidthPreview], carried over from the v2 editor.
 */
internal object BrushWidthMetrics {
  val BarLength = 174.dp
  val BarThickness = 48.dp

  /** How much of [BarThickness] hangs off the start edge of the screen at rest. */
  val RestingClip = BarThickness / 2

  /** Cross-axis size of the layout box, extended inwards to keep [BarThickness] of it touchable at rest. */
  val TouchThickness = BarThickness + RestingClip

  val RestingOffset = -RestingClip
  val SlideInDistance = 36.dp
  val SlideEasing = CubicBezierEasing(0.17f, 0.17f, 0f, 1f)
  const val SlideDurationMillis = 250

  val TrackTopThickness = 16.dp
  val TrackBottomThickness = 0.3.dp
  const val TrackAlpha = 0.6f

  val ThumbDiameter = 32.dp

  val PreviewBackdropWidth = 1.dp
  const val PreviewFadeDurationMillis = 150
}
