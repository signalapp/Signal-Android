/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import org.signal.core.ui.compose.PhonePortraitDayPreview
import org.signal.core.ui.compose.PhonePortraitNightPreview
import org.signal.core.ui.compose.Previews

/**
 * Vertical brush thickness control, anchored to the start edge of the screen.
 *
 * Sits half off-screen at rest and slides inward for the duration of a drag. The top of the track is the
 * thickest setting.
 *
 * The touchable area extends past the track towards the center of the screen so that the on-screen portion stays a
 * full [BrushWidthMetrics.BarThickness] wide even while the track is half clipped.
 */
@Composable
fun BrushWidthBar(
  fraction: Float,
  onFractionChanged: (fraction: Float, gestureComplete: Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  var isDragging by remember { mutableStateOf(false) }
  val trackPath = remember { Path() }

  val offsetX by animateDpAsState(
    targetValue = with(BrushWidthMetrics) { if (isDragging) RestingOffset + SlideInDistance else RestingOffset },
    animationSpec = tween(durationMillis = BrushWidthMetrics.SlideDurationMillis, easing = BrushWidthMetrics.SlideEasing)
  )

  Canvas(
    modifier = modifier
      .offset(x = offsetX)
      .size(width = BrushWidthMetrics.TouchThickness, height = BrushWidthMetrics.BarLength)
      .pointerInput(Unit) {
        awaitEachGesture {
          val thumbRadius = BrushWidthMetrics.ThumbDiameter.toPx() / 2f
          val down = awaitFirstDown()
          down.consume()
          isDragging = true

          var latest = fractionAt(down.position.y, size.height.toFloat(), thumbRadius)
          onFractionChanged(latest, false)

          drag(down.id) { change ->
            change.consume()
            latest = fractionAt(change.position.y, size.height.toFloat(), thumbRadius)
            onFractionChanged(latest, false)
          }

          isDragging = false
          onFractionChanged(latest, true)
        }
      }
  ) {
    drawTrack(trackPath)
    drawThumb(fraction)
  }
}

private fun fractionAt(y: Float, height: Float, thumbRadius: Float): Float {
  val travel = height - thumbRadius * 2f
  if (travel <= 0f) {
    return 0f
  }

  return (1f - (y - thumbRadius) / travel).coerceIn(0f, 1f)
}

private fun DrawScope.drawTrack(path: Path) = with(BrushWidthMetrics) {
  val centerX = BarThickness.toPx() / 2f
  val topHalfThickness = TrackTopThickness.toPx() / 2f
  val bottomHalfThickness = TrackBottomThickness.toPx() / 2f

  path.rewind()
  path.moveTo(centerX - topHalfThickness, 0f)
  path.lineTo(centerX + topHalfThickness, 0f)
  path.lineTo(centerX + bottomHalfThickness, size.height)
  path.lineTo(centerX - bottomHalfThickness, size.height)
  path.close()

  drawPath(path = path, color = Color.White, alpha = TrackAlpha)
}

private fun DrawScope.drawThumb(fraction: Float) = with(BrushWidthMetrics) {
  val radius = ThumbDiameter.toPx() / 2f
  val travel = size.height - radius * 2f

  drawCircle(
    color = Color.White,
    radius = radius,
    center = Offset(BarThickness.toPx() / 2f, radius + (1f - fraction) * travel)
  )
}

@PhonePortraitDayPreview
@PhonePortraitNightPreview
@Composable
private fun BrushWidthBarPreview() {
  Previews.Preview {
    BrushWidthBar(
      fraction = 0.5f,
      onFractionChanged = { _, _ -> },
      modifier = Modifier.offset(x = BrushWidthMetrics.BarThickness)
    )
  }
}
