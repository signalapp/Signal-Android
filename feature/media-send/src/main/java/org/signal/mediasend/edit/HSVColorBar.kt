/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.PhonePortraitDayPreview
import org.signal.core.ui.compose.PhonePortraitNightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.rememberWindowBreakpoint

@Composable
fun HSVColorBar(
  state: HSVColorBarState,
  onColorChanged: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val orientation = if (rememberWindowBreakpoint() == WindowBreakpoint.SMALL) {
    ColorBarOrientation.HORIZONTAL
  } else {
    ColorBarOrientation.VERTICAL
  }
  val colors = remember { HSVColors.composeColors }
  val thumbColor = SignalTheme.colors.colorSurface5

  Canvas(
    modifier = modifier.then(orientation.barModifier)
      .pointerInput(Unit) {
        detectTapGestures { offset ->
          state.fraction = orientation.fractionAt(
            offset = offset,
            width = size.width.toFloat(),
            height = size.height.toFloat()
          )
          onColorChanged(state.color)
        }
      }
      .pointerInput(Unit) {
        detectDragGestures { change, _ ->
          change.consume()
          state.fraction = orientation.fractionAt(
            offset = change.position,
            width = size.width.toFloat(),
            height = size.height.toFloat()
          )
          onColorChanged(state.color)
        }
      }
  ) {
    drawTrack(orientation = orientation, colors = colors)
    val thumbCenter = orientation.thumbCenter(fraction = state.fraction, size = size)
    drawThumb(
      center = thumbCenter,
      thumbColor = thumbColor,
      color = state.color
    )
  }
}

private enum class ColorBarOrientation(val barModifier: Modifier) {
  HORIZONTAL(
    Modifier
      .widthIn(max = MAX_BAR_LENGTH_DP.dp)
      .fillMaxWidth()
      .height(THUMB_DIAMETER_DP.dp)
  ),
  VERTICAL(
    Modifier
      .heightIn(max = MAX_BAR_LENGTH_DP.dp)
      .fillMaxHeight()
      .width(THUMB_DIAMETER_DP.dp)
  );

  fun fractionAt(offset: Offset, width: Float, height: Float): Float {
    val distance = when (this) {
      HORIZONTAL -> offset.x
      VERTICAL -> offset.y
    }

    val maxDistance = when (this) {
      HORIZONTAL -> width
      VERTICAL -> height
    }

    return (distance / maxDistance).coerceIn(0f, 1f)
  }

  fun thumbCenter(fraction: Float, size: Size): Offset {
    return when (this) {
      HORIZONTAL -> Offset(fraction * size.width, size.height / 2f)
      VERTICAL -> Offset(size.width / 2f, fraction * size.height)
    }
  }
}

private fun DrawScope.drawTrack(
  orientation: ColorBarOrientation,
  colors: List<Color>
) {
  val thickness = TRACK_THICKNESS_DP.dp.toPx()
  val cornerRadius = CornerRadius(thickness / 2f)

  when (orientation) {
    ColorBarOrientation.HORIZONTAL -> {
      val trackY = (size.height - thickness) / 2f
      drawRoundRect(
        brush = Brush.horizontalGradient(colors),
        topLeft = Offset(0f, trackY),
        size = Size(size.width, thickness),
        cornerRadius = cornerRadius
      )
    }

    ColorBarOrientation.VERTICAL -> {
      val trackX = (size.width - thickness) / 2f
      drawRoundRect(
        brush = Brush.verticalGradient(colors),
        topLeft = Offset(trackX, 0f),
        size = Size(thickness, size.height),
        cornerRadius = cornerRadius
      )
    }
  }
}

private fun DrawScope.drawThumb(
  center: Offset,
  thumbColor: Color,
  color: Int
) {
  val outerRadius = THUMB_DIAMETER_DP.dp.toPx() / 2f
  val borderWidth = THUMB_BORDER_DP.dp.toPx()
  val innerRadius = outerRadius - borderWidth

  drawCircle(
    color = thumbColor,
    radius = outerRadius,
    center = center
  )
  drawCircle(
    color = Color(color),
    radius = innerRadius,
    center = center
  )
}

@Stable
class HSVColorBarState {
  var fraction: Float by mutableFloatStateOf(DEFAULT_FRACTION)

  val color: Int
    get() = HSVColors.colorAt(fraction)
}

@Composable
fun rememberHSVColorBarState(): HSVColorBarState {
  return remember { HSVColorBarState() }
}

private object HSVColors {
  private const val MAX_HUE = 360
  private const val BLACK_DIVISIONS = 175
  private const val COLOR_DIVISIONS = 1023
  private const val WHITE_DIVISIONS = 125
  private const val STANDARD_LIGHTNESS = 0.4f

  val intColors: IntArray = buildColors()
  val composeColors: List<Color> = intColors.map { Color(it) }

  fun colorAt(fraction: Float): Int {
    val index = (fraction * (intColors.size - 1)).toInt().coerceIn(0, intColors.size - 1)
    return intColors[index]
  }

  private fun buildColors(): IntArray {
    val result = IntArray(BLACK_DIVISIONS + COLOR_DIVISIONS + WHITE_DIVISIONS + 3)
    var i = 0

    for (v in 0..BLACK_DIVISIONS) {
      result[i++] = ColorUtils.HSLToColor(
        floatArrayOf(MAX_HUE.toFloat(), 1f, v.toFloat() / BLACK_DIVISIONS * STANDARD_LIGHTNESS)
      )
    }

    for (hue in 0..COLOR_DIVISIONS) {
      val h = hue.toFloat() * MAX_HUE / COLOR_DIVISIONS
      result[i++] = ColorUtils.HSLToColor(
        floatArrayOf(h, 1f, calculateLightness(h, STANDARD_LIGHTNESS))
      )
    }

    for (v in 0..WHITE_DIVISIONS) {
      val endLightness = calculateLightness(MAX_HUE.toFloat(), STANDARD_LIGHTNESS)
      val lightness = endLightness + (1f - endLightness) * v.toFloat() / WHITE_DIVISIONS
      result[i++] = ColorUtils.HSLToColor(
        floatArrayOf(MAX_HUE.toFloat(), 1f, lightness)
      )
    }

    return result
  }

  private fun calculateLightness(hue: Float, valueFor60To80: Float): Float {
    return when {
      hue < 60f -> interpolate(0f, 0.45f, 60f, valueFor60To80, hue)
      hue < 180f -> valueFor60To80
      hue < 240f -> interpolate(180f, valueFor60To80, 240f, 0.5f, hue)
      hue < 300f -> interpolate(240f, 0.5f, 300f, 0.4f, hue)
      hue < 360f -> interpolate(300f, 0.4f, 360f, 0.45f, hue)
      else -> 0.45f
    }
  }

  private fun interpolate(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
    return ((y1 * (x2 - x)) + (y2 * (x - x1))) / (x2 - x1)
  }
}

private const val DEFAULT_FRACTION = 0.14f
private const val MAX_BAR_LENGTH_DP = 320
private const val TRACK_THICKNESS_DP = 20
private const val THUMB_DIAMETER_DP = 24
private const val THUMB_BORDER_DP = 6

@PhonePortraitDayPreview
@PhonePortraitNightPreview
@Composable
private fun HSVColorBarPreview() {
  Previews.Preview {
    HSVColorBar(
      state = rememberHSVColorBarState(),
      onColorChanged = {}
    )
  }
}
