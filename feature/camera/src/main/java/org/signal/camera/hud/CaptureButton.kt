/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Capture button colors matching CameraButtonView.java
 */
private object CaptureButtonColors {
  /** Background fill: white at 30% alpha (0x4CFFFFFF) */
  val Background = Color(0x4CFFFFFF)

  /** Outer ring stroke: pure white (0xFFFFFFFF) */
  val Arc = Color.White

  /** Inner fill: pure white (0xFFFFFFFF) */
  val CaptureFill = Color.White

  /** Outer stroke while recording: black at 15% alpha (0x26000000) */
  val Outline = Color(0x26000000)

  /** Record indicator: Material red (0xFFF44336) */
  val Record = Color(0xFFF44336)

  /** Progress arc: pure white (0xFFFFFFFF) */
  val Progress = Color.White
}

/**
 * Stroke widths matching CameraButtonView.java
 */
private object CaptureButtonDimensions {
  /** Stroke width for the capture arc in image mode: 3.5dp */
  val CaptureArcStrokeWidth = 3.5.dp

  /** Stroke width for the outline in video mode: 4dp */
  val OutlineStrokeWidth = 4.dp

  /** Stroke width for the progress arc: 4dp */
  val ProgressArcStrokeWidth = 4.dp

  /** Protection margin for capture fill circle: 10dp */
  val CaptureFillProtection = 10.dp

  /** Default button size */
  val DefaultButtonSize = 80.dp

  /** Default image capture size (inner area) */
  val DefaultImageCaptureSize = 60.dp

  /** Default record indicator size (red dot) */
  val DefaultRecordSize = 24.dp
}

/**
 * Drag distance multiplier for zoom calculation.
 * Matches DRAG_DISTANCE_MULTIPLIER = 3 from CameraButtonView.
 */
private const val DRAG_DISTANCE_MULTIPLIER = 3

/**
 * Deadzone reduction percentage.
 * Matches DEADZONE_REDUCTION_PERCENT = 0.35f from CameraButtonView.
 */
private const val DEADZONE_REDUCTION_PERCENT = 0.35f

/**
 * A capture button that supports both photo capture (tap) and video recording (long press).
 *
 * This composable mimics the behavior and appearance of [CameraButtonView] from the legacy
 * camera implementation. It displays:
 * - In idle state: A white-filled circle with a white arc outline
 * - In recording state: A larger circle with a red recording indicator and progress arc
 *
 * @param modifier Modifier to be applied to the button
 * @param isRecording Whether video recording is currently active
 * @param recordingProgress Progress of the recording from 0f to 1f (for progress arc display)
 * @param imageCaptureSize Size of the inner capture circle in image mode
 * @param recordSize Size of the red recording indicator circle
 * @param onTap Callback for tap gesture (photo capture)
 * @param onLongPressStart Callback when long press begins (video recording start)
 * @param onLongPressEnd Callback when long press ends (video recording stop)
 * @param onZoomChange Callback for zoom level changes during recording (0f to 1f)
 */
@Composable
fun CaptureButton(
  modifier: Modifier = Modifier,
  isRecording: Boolean,
  recordingProgress: Float = 0f,
  imageCaptureSize: Dp = CaptureButtonDimensions.DefaultImageCaptureSize,
  recordSize: Dp = CaptureButtonDimensions.DefaultRecordSize,
  onTap: () -> Unit,
  onLongPressStart: () -> Unit,
  onLongPressEnd: () -> Unit,
  onZoomChange: (Float) -> Unit
) {
  var isPressed by remember { mutableStateOf(false) }
  val scale = remember { Animatable(1f) }

  // Scale animation for press feedback and recording state
  LaunchedEffect(isPressed, isRecording) {
    val targetScale = when {
      isRecording -> 1.3f
      isPressed -> 0.9f
      else -> 1f
    }

    scale.animateTo(
      targetValue = targetScale,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = if (isRecording) Spring.StiffnessLow else Spring.StiffnessMedium
      )
    )
  }

  val density = LocalDensity.current
  val imageCaptureRadius = with(density) { imageCaptureSize.toPx() / 2f }
  val recordRadius = with(density) { recordSize.toPx() / 2f }
  val captureArcStroke = with(density) { CaptureButtonDimensions.CaptureArcStrokeWidth.toPx() }
  val outlineStroke = with(density) { CaptureButtonDimensions.OutlineStrokeWidth.toPx() }
  val progressStroke = with(density) { CaptureButtonDimensions.ProgressArcStrokeWidth.toPx() }
  val fillProtection = with(density) { CaptureButtonDimensions.CaptureFillProtection.toPx() }

  Box(
    modifier = modifier
      .size(CaptureButtonDimensions.DefaultButtonSize)
      .graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
      }
      .pointerInput(Unit) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          isPressed = true

          var longPressTriggered = false
          var startY = down.position.y
          val pressStartTime = System.currentTimeMillis()
          val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis

          // Calculate deadzone for zoom gestures
          val deadzoneTop = size.height * DEADZONE_REDUCTION_PERCENT / 2f
          val maxRange = size.height * DRAG_DISTANCE_MULTIPLIER

          try {
            while (true) {
              val event = withTimeoutOrNull(50) { awaitPointerEvent() }

              if (event != null) {
                val currentPointer = event.changes.firstOrNull { it.id == down.id }

                if (currentPointer == null || !currentPointer.pressed) {
                  // Finger lifted
                  if (!longPressTriggered) {
                    onTap()
                  } else {
                    onLongPressEnd()
                  }
                  break
                }

                // Check for long press timeout
                val elapsed = System.currentTimeMillis() - pressStartTime
                if (!longPressTriggered && elapsed >= longPressTimeoutMs) {
                  longPressTriggered = true
                  startY = currentPointer.position.y
                  onLongPressStart()
                }

                // Handle zoom during recording
                if (longPressTriggered) {
                  val isAboveDeadzone = currentPointer.position.y < deadzoneTop
                  if (isAboveDeadzone) {
                    val deltaY = (deadzoneTop - currentPointer.position.y).coerceAtLeast(0f)
                    val zoomPercent = (deltaY / maxRange).coerceIn(0f, 1f)
                    // Apply decelerate interpolation like CameraButtonView
                    val interpolatedZoom = decelerateInterpolation(zoomPercent)
                    onZoomChange(interpolatedZoom)
                  }
                }

                currentPointer.consume()
              } else {
                // Timeout - check for long press
                val elapsed = System.currentTimeMillis() - pressStartTime
                if (!longPressTriggered && elapsed >= longPressTimeoutMs) {
                  longPressTriggered = true
                  startY = down.position.y
                  onLongPressStart()
                }
              }
            }
          } finally {
            isPressed = false
          }
        }
      },
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.matchParentSize()) {
      if (isRecording) {
        drawForVideoCapture(
          recordRadius = recordRadius,
          outlineStroke = outlineStroke,
          progressStroke = progressStroke,
          progressPercent = recordingProgress
        )
      } else {
        drawForImageCapture(
          captureRadius = imageCaptureRadius,
          arcStroke = captureArcStroke,
          fillProtection = fillProtection
        )
      }
    }
  }
}

/**
 * Decelerate interpolation matching DecelerateInterpolator from Android.
 * Formula: 1.0 - (1.0 - input)^2
 */
private fun decelerateInterpolation(input: Float): Float {
  return 1f - (1f - input) * (1f - input)
}

/**
 * Draw the button in image capture mode.
 */
private fun DrawScope.drawForImageCapture(
  captureRadius: Float,
  arcStroke: Float,
  fillProtection: Float
) {
  val centerX = size.width / 2f
  val centerY = size.height / 2f
  val radius = min(centerX, centerY)

  // Background circle
  drawCircle(
    color = CaptureButtonColors.Background,
    radius = radius,
    center = Offset(centerX, centerY)
  )

  // Arc outline
  drawCircle(
    color = CaptureButtonColors.Arc,
    radius = radius,
    center = Offset(centerX, centerY),
    style = Stroke(width = arcStroke)
  )

  // Inner fill circle (smaller to create the ring effect)
  drawCircle(
    color = CaptureButtonColors.CaptureFill,
    radius = radius - fillProtection,
    center = Offset(centerX, centerY)
  )
}

/**
 * Draw the button in video capture mode.
 */
private fun DrawScope.drawForVideoCapture(
  recordRadius: Float,
  outlineStroke: Float,
  progressStroke: Float,
  progressPercent: Float
) {
  val centerX = size.width / 2f
  val centerY = size.height / 2f
  val radius = min(centerX, centerY)

  // Background circle
  drawCircle(
    color = CaptureButtonColors.Background,
    radius = radius,
    center = Offset(centerX, centerY)
  )

  // Outline stroke
  drawCircle(
    color = CaptureButtonColors.Outline,
    radius = radius,
    center = Offset(centerX, centerY),
    style = Stroke(width = outlineStroke)
  )

  // Red record indicator
  drawCircle(
    color = CaptureButtonColors.Record,
    radius = recordRadius,
    center = Offset(centerX, centerY)
  )

  // Progress arc (only if there's progress to show)
  if (progressPercent > 0f) {
    val strokeHalf = progressStroke / 2f
    drawArc(
      color = CaptureButtonColors.Progress,
      startAngle = -90f, // Start from top
      sweepAngle = 360f * progressPercent,
      useCenter = false,
      topLeft = Offset(strokeHalf, strokeHalf),
      size = Size(size.width - progressStroke, size.height - progressStroke),
      style = Stroke(width = progressStroke, cap = StrokeCap.Round)
    )
  }
}

@Preview(name = "Idle State", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun CaptureButtonIdlePreview() {
  Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
    CaptureButton(
      isRecording = false,
      onTap = {},
      onLongPressStart = {},
      onLongPressEnd = {},
      onZoomChange = {}
    )
  }
}

@Preview(name = "Recording State", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun CaptureButtonRecordingPreview() {
  Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
    CaptureButton(
      isRecording = true,
      recordingProgress = 0f,
      onTap = {},
      onLongPressStart = {},
      onLongPressEnd = {},
      onZoomChange = {}
    )
  }
}

@Preview(name = "Recording with Progress", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun CaptureButtonRecordingWithProgressPreview() {
  Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
    CaptureButton(
      isRecording = true,
      recordingProgress = 0.65f,
      onTap = {},
      onLongPressStart = {},
      onLongPressEnd = {},
      onZoomChange = {}
    )
  }
}
