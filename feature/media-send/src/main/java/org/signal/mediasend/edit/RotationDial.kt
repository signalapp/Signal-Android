/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.imageeditor.core.model.EditorModel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val MAX_DEGREES: Float = 44.99999f
private const val MIN_DEGREES: Float = -44.99999f

private val SPACE_BETWEEN_INDICATORS = 12.dp
private val INDICATOR_WIDTH = 1.dp
private val MINOR_INDICATOR_HEIGHT = 12.dp
private val MAJOR_INDICATOR_HEIGHT = 24.dp

@Composable
fun RotationDial(
  imageEditorController: EditorController.Image,
  modifier: Modifier = Modifier
) {
  val hapticFeedback = LocalHapticFeedback.current
  val spaceBetweenIndicatorsPx = with(LocalDensity.current) { SPACE_BETWEEN_INDICATORS.toPx() }

  var degrees by remember { mutableFloatStateOf(imageEditorController.dialRotation) }
  var isInGesture by remember { mutableStateOf(false) }

  val snapDegrees = calculateSnapDegrees(degrees, isInGesture)
  val dialDegrees = getDialDegrees(snapDegrees)
  val displayDegree = dialDegrees.roundToInt()

  val modFiveColor = MaterialTheme.colorScheme.onSurface
  val minorColor = MaterialTheme.colorScheme.outline

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(56.dp)
      .clip(RoundedCornerShape(percent = 50))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .pointerInput(Unit) {
        awaitEachGesture {
          val down = awaitFirstDown()
          down.consume()

          isInGesture = true
          degrees = imageEditorController.dialRotation
          imageEditorController.onDialGestureStart()

          val dragged = drag(down.id) { change ->
            val dragAmount = change.positionChange().x
            change.consume()

            val degreeIncrement = -dragAmount / spaceBetweenIndicatorsPx
            val prevDialDegrees = getDialDegrees(degrees)
            val newDialDegrees = getDialDegrees(degrees + degreeIncrement)

            val offEndOfMax = prevDialDegrees >= MAX_DEGREES / 2f && newDialDegrees <= MIN_DEGREES / 2f
            val offEndOfMin = newDialDegrees >= MAX_DEGREES / 2f && prevDialDegrees <= MIN_DEGREES / 2f

            if (prevDialDegrees.roundToInt() != newDialDegrees.roundToInt()) {
              hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }

            val newDegrees = when {
              offEndOfMax -> degrees + (MAX_DEGREES - prevDialDegrees)
              offEndOfMin -> degrees - (MAX_DEGREES - abs(prevDialDegrees))
              else -> degrees + degreeIncrement
            }

            degrees = newDegrees
            val newSnapDegrees = calculateSnapDegrees(newDegrees, true)
            imageEditorController.onDialRotationChanged(newSnapDegrees)
          }

          isInGesture = false
          val finalSnapDegrees = calculateSnapDegrees(degrees, false)
          imageEditorController.onDialRotationChanged(finalSnapDegrees)
          imageEditorController.onDialGestureEnd()
        }
      }
  ) {
    // Tick marks layer (full width, centered on pill center)
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
      val canvasWidth = size.width
      val canvasHeight = size.height
      val centerY = canvasHeight / 2f
      val indicatorWidthPx = INDICATOR_WIDTH.toPx()
      val minorHeightPx = MINOR_INDICATOR_HEIGHT.toPx()
      val majorHeightPx = MAJOR_INDICATOR_HEIGHT.toPx()
      val spacingPx = SPACE_BETWEEN_INDICATORS.toPx()

      val approximateCenterDegree = dialDegrees.roundToInt()
      val fractionalOffset = dialDegrees - approximateCenterDegree
      val dialOffset = spacingPx * fractionalOffset

      val centerX = canvasWidth / 2f
      val startX = centerX - dialOffset

      fun heightForDegree(degree: Int): Float = if (degree == 0) majorHeightPx else minorHeightPx

      // Draw rightward from center
      var currentDegree = approximateCenterDegree
      var x = startX
      while (x < canvasWidth && currentDegree <= ceil(MAX_DEGREES).toInt()) {
        val h = heightForDegree(currentDegree)
        val color = if (currentDegree % 5 == 0) modFiveColor else minorColor
        drawRect(
          color = color,
          topLeft = Offset(x - indicatorWidthPx / 2f, centerY - h / 2f),
          size = Size(indicatorWidthPx, h)
        )
        x += spacingPx
        currentDegree += 1
      }

      // Draw leftward from center
      currentDegree = approximateCenterDegree - 1
      x = startX - spacingPx
      while (x >= 0 && currentDegree >= floor(MIN_DEGREES).toInt()) {
        val h = heightForDegree(currentDegree)
        val color = if (currentDegree % 5 == 0) modFiveColor else minorColor
        drawRect(
          color = color,
          topLeft = Offset(x - indicatorWidthPx / 2f, centerY - h / 2f),
          size = Size(indicatorWidthPx, h)
        )
        x -= spacingPx
        currentDegree -= 1
      }

      // Draw the center-most indicator on top
      val centerHeight = heightForDegree(approximateCenterDegree)
      drawRect(
        color = modFiveColor,
        topLeft = Offset(centerX - indicatorWidthPx / 2f, centerY - centerHeight / 2f),
        size = Size(indicatorWidthPx, centerHeight)
      )
    }

    // Degree text overlay (on top of ticks, with background to cover them)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxHeight()
    ) {
      Text(
        text = "$displayDegree",
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        modifier = Modifier
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .padding(start = 16.dp, end = 24.dp)
      )

      Dividers.Vertical(
        thickness = Dp.Hairline,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.fillMaxHeight().padding(vertical = 22.dp)
      )
    }
  }
}

private fun getDialDegrees(degrees: Float): Float {
  val alpha = degrees % 360f

  if (alpha % 90 == 0f) {
    return 0f
  }

  val beta = floor(alpha / 90f)
  val offset = alpha - beta * 90f

  return if (offset > 45f) {
    offset - 90f
  } else {
    offset
  }
}

private fun calculateSnapDegrees(degrees: Float, isInGesture: Boolean): Float {
  return if (isInGesture) {
    val dialDegrees = getDialDegrees(degrees)
    if (dialDegrees.roundToInt() == 0) {
      degrees - dialDegrees
    } else {
      degrees
    }
  } else {
    degrees
  }
}

@Preview
@Composable
fun RotationDialPreview() {
  Previews.Preview {
    RotationDial(
      imageEditorController = remember {
        EditorController.Image(EditorModel.create(0))
      }
    )
  }
}
