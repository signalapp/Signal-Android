package org.signal.core.ui.copied.androidx.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CancellationException

/**
 * Modified version of detectDragGesturesAfterLongPress from [androidx.compose.foundation.gestures.DragGestureDetector]
 * that allows you to optionally offset the starting and ending position of the draggable area
 */
suspend fun PointerInputScope.detectDragGestures(
  onDragStart: (Offset) -> Unit = { },
  onDragEnd: () -> Unit = { },
  onDragCancel: () -> Unit = { },
  onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
  leftDpOffset: Dp = 0.dp,
  rightDpOffset: Dp
) {
  awaitEachGesture {
    try {
      val down = awaitFirstDown(requireUnconsumed = false)
      val drag = awaitLongPressOrCancellation(down.id)
      if (drag != null && (drag.position.x > leftDpOffset.toPx()) && (drag.position.x < rightDpOffset.toPx())) {
        onDragStart.invoke(drag.position)

        if (
          drag(drag.id) {
            onDrag(it, it.positionChange())
            it.consume()
          }
        ) {
          // consume up if we quit drag gracefully with the up
          currentEvent.changes.fastForEach {
            if (it.changedToUp()) it.consume()
          }
          onDragEnd()
        } else {
          onDragCancel()
        }
      }
    } catch (c: CancellationException) {
      onDragCancel()
      throw c
    }
  }
}

/**
 * Modified version of awaitLongPressOrCancellation from [androidx.compose.foundation.gestures.DragGestureDetector] with a reduced long press timeout
 */
suspend fun AwaitPointerEventScope.awaitLongPressOrCancellation(
  pointerId: PointerId
): PointerInputChange? {
  if (currentEvent.isPointerUp(pointerId)) {
    return null // The pointer has already been lifted, so the long press is cancelled.
  }

  val initialDown =
    currentEvent.changes.fastFirstOrNull { it.id == pointerId } ?: return null

  var longPress: PointerInputChange? = null
  var currentDown = initialDown
  val longPressTimeout = (viewConfiguration.longPressTimeoutMillis / 100)
  return try {
    // wait for first tap up or long press
    withTimeout(longPressTimeout) {
      var finished = false
      while (!finished) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.fastAll { it.changedToUpIgnoreConsumed() }) {
          // All pointers are up
          finished = true
        }

        if (
          event.changes.fastAny {
            it.isConsumed || it.isOutOfBounds(size, extendedTouchPadding)
          }
        ) {
          finished = true // Canceled
        }

        // Check for cancel by position consumption. We can look on the Final pass of
        // the existing pointer event because it comes after the Main pass we checked
        // above.
        val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
        if (consumeCheck.changes.fastAny { it.isConsumed }) {
          finished = true
        }
        if (event.isPointerUp(currentDown.id)) {
          val newPressed = event.changes.fastFirstOrNull { it.pressed }
          if (newPressed != null) {
            currentDown = newPressed
            longPress = currentDown
          } else {
            // should technically never happen as we checked it above
            finished = true
          }
          // Pointer (id) stayed down.
        } else {
          longPress = event.changes.fastFirstOrNull { it.id == currentDown.id }
        }
      }
    }
    null
  } catch (_: PointerEventTimeoutCancellationException) {
    longPress ?: initialDown
  }
}

private fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
  changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
