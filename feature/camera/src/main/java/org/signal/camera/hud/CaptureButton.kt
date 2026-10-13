/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import org.signal.camera.R
import org.signal.camera.test.TestTags

private object CaptureButtonColors {
  val Background: Color
    @Composable get() = colorResource(R.color.CameraHud_control_background)

  val CaptureFill: Color
    @Composable get() = colorResource(R.color.CameraHud_control_foreground)

  val Record: Color
    @Composable get() = colorResource(R.color.CameraHud_control_red_background)
}

/** Every measurement the button is drawn from, so there is one place to change any of them. */
internal object CaptureButtonDimensions {
  /** The outer circle, which every state shares. */
  val ButtonSize = 76.dp

  /** The inner shape while the button is idle, in either mode. */
  val IdleSize = 64.dp

  /** What the inner shape shrinks to under a finger. */
  val PressedSize = 56.dp

  /** A held recording stays at the size the press that started it left the shape at. */
  val HeldRecordingSize = PressedSize

  val LockedRecordingSize = 44.dp
  val LockedRecordingCornerRadius = 10.dp

  /** The circle dragged to the lock, which is the lock button's own size. */
  val LockDraggableSize = RecordingActionButtonSize

  /**
   * How far past the lock's own edge still counts as being over it. The thumb covers the lock on the way there, so a
   * target no bigger than the button itself is hard to feel for.
   */
  val LockSnapMargin = 20.dp

  /**
   * How much further out than [LockSnapMargin] the finger has to come back before the lock releases. Without the
   * hysteresis the lock chatters on and off as a thumb wavers on the boundary.
   */
  val LockSnapRelease = 12.dp

  /**
   * How far a drag has to have carried toward the lock before it counts as headed there. Below it the drag still belongs
   * to the zoom, so a vertical zoom drag that wanders a pixel sideways does not lose it.
   */
  val LockDragSlop = 8.dp

  /** The press shrinks by scaling, so the two sizes above stay the only place either number is written down. */
  val PressedScale = PressedSize / IdleSize
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

/** Played when the lock takes hold, which the thumb covering it is otherwise no way to know. */
private val LockSnapHaptic = HapticFeedbackType.SegmentTick

/**
 * Played when the lock is taken. [HapticFeedbackType.GestureThresholdActivate] is what this is in name, but below API 34
 * it falls back to a context click, which is too faint to feel through a thumb that is mid-drag.
 */
private val LockHaptic = HapticFeedbackType.LongPress

/** Size and corner radius share a spec so a circle stays circular on the way to a square. */
private val ShapeSpec = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
private val ColorSpec = tween<Color>(durationMillis = 200)
private val PressSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

/** Slacker than [ShapeSpec] so the inner shape trails the finger on the way to the lock rather than tracking it. */
private val LockDragSpec = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

/** Everything the button animates between [CaptureButtonState]s. */
private data class CaptureButtonInnerShape(val size: Dp, val cornerRadius: Dp, val color: Color)

private val CaptureButtonState.innerShape: CaptureButtonInnerShape
  @Composable get() = when (this) {
    CaptureButtonState.PHOTO -> CaptureButtonInnerShape(
      size = CaptureButtonDimensions.IdleSize,
      cornerRadius = CaptureButtonDimensions.IdleSize / 2,
      color = CaptureButtonColors.CaptureFill
    )

    CaptureButtonState.VIDEO -> CaptureButtonInnerShape(
      size = CaptureButtonDimensions.IdleSize,
      cornerRadius = CaptureButtonDimensions.IdleSize / 2,
      color = CaptureButtonColors.Record
    )

    CaptureButtonState.RECORDING_HELD -> CaptureButtonInnerShape(
      size = CaptureButtonDimensions.HeldRecordingSize,
      cornerRadius = CaptureButtonDimensions.HeldRecordingSize / 2,
      color = CaptureButtonColors.Record
    )

    CaptureButtonState.RECORDING_LOCKED -> CaptureButtonInnerShape(
      size = CaptureButtonDimensions.LockedRecordingSize,
      cornerRadius = CaptureButtonDimensions.LockedRecordingCornerRadius,
      color = CaptureButtonColors.Record
    )
  }

/**
 * A capture button that supports both photo capture (tap) and video recording (long press).
 *
 * The outer circle is fixed; the inner shape animates its size, corners and color between the [CaptureButtonState]s: a
 * white circle for a photo, a red one for a recording waiting to start, a small red circle while a held recording runs,
 * and a red rounded square while a locked one runs.
 *
 * @param state Which of the button's looks to show, from [CaptureButtonState.of]
 * @param onTap Callback for tap gesture, whose meaning is the caller's to decide from [state]
 * @param onLongPressStart Callback when long press begins (video recording start)
 * @param onLongPressEnd Callback when long press ends (video recording stop)
 * @param onZoomChange Callback for zoom level changes during recording (0f to 1f)
 * @param onLock Callback when a drag has reached the lock, asking for the recording to run unheld
 * @param onOverLockChanged Callback when a drag arrives over the lock or leaves it again, so the lock can show that
 *   lifting there is what takes it. The circle this button carries there arrives underneath it, so the lock is what is
 *   seen at the end of the drag.
 * @param lockOffset Where the lock sits relative to this button's center, in pixels of this button's own frame.
 *   [Offset.Zero] for a recording that has no lock to be dragged to.
 * @param modifier Modifier to be applied to the button
 */
@Composable
fun CaptureButton(
  state: CaptureButtonState,
  onTap: () -> Unit,
  onLongPressStart: () -> Unit,
  onLongPressEnd: () -> Unit,
  onZoomChange: (Float) -> Unit,
  onLock: () -> Unit = {},
  onOverLockChanged: (Boolean) -> Unit = {},
  lockOffset: Offset = Offset.Zero,
  modifier: Modifier = Modifier
) {
  var isPressed by remember { mutableStateOf(false) }

  /** How far the drag has carried toward the lock, from nothing to the whole way. */
  var lockProgress by remember { mutableFloatStateOf(0f) }

  val haptics = LocalHapticFeedback.current

  // The gesture detector is set up once and never restarted, so the callbacks are read through here rather than
  // captured — a captured callback would answer for the state at first composition.
  val currentOnTap by rememberUpdatedState(onTap)
  val currentOnLongPressStart by rememberUpdatedState(onLongPressStart)
  val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
  val currentOnZoomChange by rememberUpdatedState(onZoomChange)
  val currentOnLock by rememberUpdatedState(onLock)
  val currentOnOverLockChanged by rememberUpdatedState(onOverLockChanged)
  val currentLockOffset by rememberUpdatedState(lockOffset)

  // A drag toward the lock takes the shape part of the way to what it will be once it gets there, so the button shows
  // what letting go would leave behind before the finger commits to it.
  val isDraggingToLock = lockProgress > 0f && state == CaptureButtonState.RECORDING_HELD
  val innerShape = if (isDraggingToLock) {
    val held = CaptureButtonState.RECORDING_HELD.innerShape
    val locked = CaptureButtonState.RECORDING_LOCKED.innerShape

    // Both ends of the drag are the recording red, so only the size and the corners have anywhere to go.
    CaptureButtonInnerShape(
      size = lerp(held.size, locked.size, lockProgress),
      cornerRadius = lerp(held.cornerRadius, locked.cornerRadius, lockProgress),
      color = locked.color
    )
  } else {
    state.innerShape
  }

  val shapeSpec = if (isDraggingToLock) LockDragSpec else ShapeSpec
  val innerSize by animateDpAsState(targetValue = innerShape.size, animationSpec = shapeSpec, label = "CaptureButtonSize")
  val innerCornerRadius by animateDpAsState(targetValue = innerShape.cornerRadius, animationSpec = shapeSpec, label = "CaptureButtonCornerRadius")
  val innerColor by animateColorAsState(targetValue = innerShape.color, animationSpec = ColorSpec, label = "CaptureButtonColor")
  // The press shrinks the idle circle to the size a held recording runs at, so a hold that becomes one does not move
  // again. A recording carries that size itself, so the scale lifts as it starts rather than shrinking twice.
  val pressedScale by animateFloatAsState(
    targetValue = if (isPressed && !state.isRecording) CaptureButtonDimensions.PressedScale else 1f,
    animationSpec = PressSpec,
    label = "CaptureButtonPressedScale"
  )

  Box(
    modifier = modifier
      .size(CaptureButtonDimensions.ButtonSize)
      .testTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON)
      .pointerInput(Unit) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          isPressed = true

          val deadzoneTop = size.height * DEADZONE_REDUCTION_PERCENT / 2f
          val deadzoneBottom = size.height * (1f - DEADZONE_REDUCTION_PERCENT / 2f)
          val maxRange = size.height * DRAG_DISTANCE_MULTIPLIER

          val buttonCenter = Offset(size.width / 2f, size.height / 2f)
          val lockRadius = CaptureButtonDimensions.LockDraggableSize.toPx() / 2f
          val snapTo = lockRadius + CaptureButtonDimensions.LockSnapMargin.toPx()
          val snapOff = snapTo + CaptureButtonDimensions.LockSnapRelease.toPx()
          val lockDragSlop = CaptureButtonDimensions.LockDragSlop.toPx()

          // The lock takes hold further out than its own edge and does not release until the finger is further out
          // still, so it snaps on rather than having to be held on. Offset.Zero means no lock is on offer, which would
          // otherwise resolve to the button's own center.
          fun isOverLock(position: Offset, wasOver: Boolean): Boolean {
            val offset = currentLockOffset

            if (offset == Offset.Zero) {
              return false
            }

            return (position - (buttonCenter + offset)).getDistance() <= if (wasOver) snapOff else snapTo
          }

          try {
            // The press becomes a hold when the timeout expires rather than when the finger does anything, so it
            // arrives as the cancellation of the wait for a lift.
            var isHeld = false
            val liftedEarly = try {
              withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
            } catch (_: PointerEventTimeoutCancellationException) {
              isHeld = true
              null
            }

            when {
              isHeld -> Unit
              // A press let go of before the timeout is a tap. Anything else is the gesture being cancelled.
              liftedEarly != null -> {
                currentOnTap()
                return@awaitEachGesture
              }

              else -> return@awaitEachGesture
            }

            currentOnLongPressStart()

            // Nothing waits with a timeout from here on. An event that arrived while one was expiring would be dropped,
            // and with a finger holding still the only event of the whole gesture is the lift that ends it.

            // Whether the finger was over the lock when it was last seen, which is what the lift is judged against.
            var overLock = false

            while (true) {
              val pointer = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break

              val wasOverLock = overLock
              overLock = isOverLock(pointer.position, wasOverLock)

              // Taking hold is felt as it happens, and the lock is told so it can show it, so the finger knows it has
              // arrived without having to commit to find out. Only the crossing is reported, not every event after it.
              if (overLock != wasOverLock) {
                currentOnOverLockChanged(overLock)

                if (overLock) {
                  haptics.performHapticFeedback(LockSnapHaptic)
                }
              }

              if (!pointer.pressed) {
                break
              }

              // A drag is only headed for the lock once it has carried past the slop; below that it still belongs to
              // the zoom, so a vertical drag that wanders a pixel sideways does not silently lose it. Being over the
              // lock finishes the morph however far short the travel counts, so what the button shows agrees with what
              // letting go would do.
              val lockTravel = currentLockOffset.fractionTraveled(pointer.position - down.position)
              val isHeadedForLock = overLock || lockTravel * currentLockOffset.getDistance() > lockDragSlop

              lockProgress = when {
                overLock -> 1f
                isHeadedForLock -> lockTravel
                else -> 0f
              }

              val zoom = when {
                isHeadedForLock -> null
                pointer.position.y < deadzoneTop -> decelerateInterpolation(((deadzoneTop - pointer.position.y) / maxRange).coerceIn(0f, 1f))
                pointer.position.y > deadzoneBottom -> -decelerateInterpolation(((pointer.position.y - deadzoneBottom) / maxRange).coerceIn(0f, 1f))
                else -> null
              }

              if (zoom != null) {
                currentOnZoomChange(zoom)
              }

              pointer.consume()
            }

            // Reaching the lock is not what takes it: the finger has to come off over it. A drag that crosses the lock
            // on its way elsewhere, or that backs off it before lifting, leaves the recording as it was.
            if (overLock) {
              haptics.performHapticFeedback(LockHaptic)
              currentOnLock()
            } else {
              currentOnLongPressEnd()
            }
          } finally {
            isPressed = false
            lockProgress = 0f
            currentOnOverLockChanged(false)
          }
        }
      },
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .background(color = CaptureButtonColors.Background, shape = CircleShape)
    )

    Box(
      modifier = Modifier
        .size(innerSize)
        .graphicsLayer {
          scaleX = pressedScale
          scaleY = pressedScale
        }
        .background(color = innerColor, shape = RoundedCornerShape(innerCornerRadius))
    )

    // The circle the finger carries to the lock. It runs on the line between the two rather than following the finger
    // exactly, so a drag that wanders still arrives, and it is not sprung — only the shape it leaves behind is.
    if (isDraggingToLock) {
      Box(
        modifier = Modifier
          .size(CaptureButtonDimensions.LockDraggableSize)
          .graphicsLayer {
            translationX = currentLockOffset.x * lockProgress
            translationY = currentLockOffset.y * lockProgress
          }
          .background(color = CaptureButtonColors.Record, shape = CircleShape)
      )
    }
  }
}

/**
 * How far toward this offset a [drag] has carried, as a fraction of the whole distance. Only the component along the way
 * there counts, so a drag across it gets no closer and one past it goes no further.
 *
 * Zero for [Offset.Zero], which is what a recording with no lock on offer has.
 */
private fun Offset.fractionTraveled(drag: Offset): Float {
  val distanceSquared = x * x + y * y

  return if (distanceSquared > 0f) ((drag.x * x + drag.y * y) / distanceSquared).coerceIn(0f, 1f) else 0f
}

/**
 * Decelerate interpolation matching DecelerateInterpolator from Android.
 * Formula: 1.0 - (1.0 - input)^2
 */
private fun decelerateInterpolation(input: Float): Float {
  return 1f - (1f - input) * (1f - input)
}

@Preview(name = "Every state", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun CaptureButtonStatesPreview() {
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    CaptureButtonState.entries.forEach { state ->
      CaptureButton(
        state = state,
        onTap = {},
        onLongPressStart = {},
        onLongPressEnd = {},
        onZoomChange = {}
      )
    }
  }
}

/**
 * The animations between the states, for the interactive preview: a tap steps to the next state and a hold runs the held
 * recording for as long as it is held, matching what the camera does with the same gestures.
 *
 * The canvas is given a fixed size, and the state's name a whole line of it, so a longer name cannot widen the canvas
 * out from under the button and leave it stretched.
 */
@Preview(name = "Animated between states", showBackground = true, backgroundColor = 0xFF444444, widthDp = 240, heightDp = 220)
@Composable
private fun CaptureButtonInteractivePreview() {
  var stateIndex by remember { mutableIntStateOf(0) }
  var heldState: CaptureButtonState? by remember { mutableStateOf(null) }

  val steppedState = CaptureButtonState.entries[stateIndex % CaptureButtonState.entries.size]
  val state = heldState ?: steppedState

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    modifier = Modifier.fillMaxSize()
  ) {
    Text(
      text = state.name,
      color = Color.White,
      maxLines = 1,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )

    CaptureButton(
      state = state,
      onTap = { stateIndex++ },
      onLongPressStart = { heldState = CaptureButtonState.RECORDING_HELD },
      onLongPressEnd = { heldState = null },
      onZoomChange = {}
    )

    Button(onClick = { stateIndex++ }) {
      Text(text = "Next state")
    }
  }
}
