/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import android.graphics.Matrix
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.signal.imageeditor.core.Bounds

/**
 * Shows the size of the brush the user is about to draw with, centered on the editor.
 *
 * Blur has no color of its own, so it gets the backdrop ring only.
 */
@Composable
fun BrushWidthPreview(
  visible: Boolean,
  thickness: Float,
  viewMatrix: Matrix,
  color: Color,
  isBlur: Boolean,
  modifier: Modifier = Modifier
) {
  val alpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(durationMillis = BrushWidthMetrics.PreviewFadeDurationMillis)
  )

  if (alpha <= 0f) {
    return
  }

  Canvas(modifier = modifier) {
    val radius = viewMatrix.mapRadius(thickness * Bounds.FULL_BOUNDS.width() / 2f)
    val center = Offset(size.width / 2f, size.height / 2f)

    drawCircle(
      color = Color.White,
      radius = radius + BrushWidthMetrics.PreviewBackdropWidth.toPx(),
      center = center,
      alpha = alpha
    )

    if (!isBlur) {
      drawCircle(
        color = color,
        radius = radius,
        center = center,
        alpha = alpha
      )
    }
  }
}
