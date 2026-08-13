/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import android.graphics.Matrix
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.signal.imageeditor.core.ImageEditorTouchHandler
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.renderers.MultiLineTextRenderer
import org.signal.mediasend.screens.edit.ChromeInsets
import org.signal.mediasend.screens.edit.ImageController

@Composable
internal fun ImageEditor(
  controller: ImageController,
  contentInsets: ChromeInsets,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val state = controller.imageEditorState
  val hapticFeedback = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()

  // Masks everything outside the image, so it has to match the surface the editor is drawn on to read as a background.
  val blackoutColor = MaterialTheme.colorScheme.surface.toArgb()

  DisposableEffect(state) {
    state.attach()
    onDispose { state.detach() }
  }

  LaunchedEffect(state, contentInsets) {
    state.setContentInsets(contentInsets)
  }

  LaunchedEffect(controller.isDraggedElementOverTrash) {
    if (controller.isDraggedElementOverTrash) {
      hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
  }

  Box(modifier = modifier) {
    Canvas(
      modifier = Modifier
        .matchParentSize()
        .clipToBounds()
        .onSizeChanged { state.setCanvasSize(it.width.toFloat(), it.height.toFloat()) }
        .imageEditorPointerInput(state, controller, scope)
    ) {
      state.revision

      val nativeCanvas = drawContext.canvas.nativeCanvas
      val rendererContext = state.getOrCreateRendererContext(context, nativeCanvas)
      rendererContext.setBlackoutColor(blackoutColor)
      rendererContext.save()
      try {
        rendererContext.canvasMatrix.initial(state.viewMatrix)
        state.editorModel.draw(rendererContext, state.textEditingElement)
      } finally {
        rendererContext.restore()
      }
    }

    if (controller.textEditingElement != null) {
      HiddenTextInput(controller = controller)
    }
  }
}

@Composable
private fun HiddenTextInput(controller: ImageController) {
  val element = controller.textEditingElement

  // Re-opening an existing element has to start from its current text, or the first keystroke would replace it.
  var text by remember(element) {
    val existing = (element?.renderer as? MultiLineTextRenderer)?.text ?: ""
    mutableStateOf(TextFieldValue(existing, TextRange(existing.length)))
  }

  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  BasicTextField(
    value = text,
    onValueChange = { newValue ->
      text = newValue
      controller.onTextChanged(newValue.text)
      controller.onTextSelectionChanged(newValue.selection.start, newValue.selection.end)
    },
    modifier = Modifier
      .size(1.dp)
      .alpha(0f)
      .focusRequester(focusRequester),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.None)
  )

  LaunchedEffect(element) {
    focusRequester.requestFocus()

    // Also zooms the element clear of the keyboard, which otherwise would not happen until the first keystroke.
    controller.onTextSelectionChanged(text.selection.start, text.selection.end)
  }

  DisposableEffect(Unit) {
    onDispose { keyboardController?.hide() }
  }
}

/** How far a finger travels before a touch on an element counts as a drag. */
private const val MAX_MOVE_SQUARED_BEFORE_DRAG = 10f

private fun Modifier.imageEditorPointerInput(state: ImageEditorState, controller: ImageController, scope: CoroutineScope): Modifier {
  return this.pointerInput(controller, controller.textEditingElement) {
    val touchHandler = ImageEditorTouchHandler()

    // The tap that a second one has to land on, and beat the timeout from, to count as a double tap.
    var lastTapElement: EditorElement? = null
    var lastTapUptimeMillis = 0L

    // The equivalent for the canvas itself, which only zoom mode taps.
    var lastCanvasTapUptimeMillis = 0L

    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = true)

      if (controller.mode == ImageController.Mode.ZOOM) {
        lastCanvasTapUptimeMillis = zoomGesture(controller, down, lastCanvasTapUptimeMillis, scope)
        return@awaitEachGesture
      }

      if (state.textEditingElement != null) {
        // During text editing, a tap on the canvas finishes editing
        down.consume()
        while (true) {
          val event = awaitPointerEvent()
          val anyPressed = event.changes.any { it.pressed }
          event.changes.forEach { it.consume() }
          if (!anyPressed) {
            controller.finishTextEditing()
            break
          }
        }
        return@awaitEachGesture
      }

      touchHandler.setDrawing(state.isDrawing, state.isBlur)
      touchHandler.setDrawingBrush(state.drawColor, state.drawThickness, state.drawCap)
      val hitElement = touchHandler.onDown(state.editorModel, state.viewMatrix, down.position.toPointF())

      if (!state.isDrawing && !state.isBlur) {
        controller.onEntityDown(hitElement)
      }

      val hasNoSession = !touchHandler.hasActiveSession()

      // Only at rest: every other mode has already spent two fingers on the elements or on the crop frame.
      val canZoomCanvas = hasNoSession && controller.mode == ImageController.Mode.NONE

      // Crop is excluded: two fingers there scale the image inside the crop frame.
      val canWaitForSecondPointer = hasNoSession && controller.mode != ImageController.Mode.CROP
      if (canWaitForSecondPointer && !awaitSecondPointer()) {
        // In NONE mode the pager took the swipe; anywhere else it was a tap on nothing, which still deselects.
        if (controller.mode != ImageController.Mode.NONE) {
          touchHandler.onUp(state.editorModel)
          lastTapElement = null
          controller.onEntitySingleTap(null)
        }
        return@awaitEachGesture
      }

      down.consume()
      state.isGestureActive = true

      var previousPointerCount = 1
      var inDrag = false
      var draggedElement: EditorElement? = null
      var droppedOnTrash = false
      var didPinch = false
      var zoomPointers: Pair<Offset, Offset>? = null

      try {
        while (true) {
          val event = awaitPointerEvent()
          val currentPressed = event.changes.filter { it.pressed }
          val currentCount = currentPressed.size

          if (currentCount == 0) {
            event.changes.forEach { it.consume() }

            if (inDrag) {
              val upPoint = (event.changes.firstOrNull()?.position ?: down.position).toPointF()
              droppedOnTrash = previousPointerCount == 1 &&
                touchHandler.checkTrashIntersect(state.editorModel, upPoint) &&
                state.editorModel.findElementAtPoint(upPoint, state.viewMatrix, Matrix()) === draggedElement
            }

            // A tap that neither painted, dragged nor pinched leaves the model exactly as it found it, so it must not
            // count as an edit -- otherwise merely selecting something would arm the "Discard changes?" prompt.
            val didModifyModel = inDrag || didPinch || touchHandler.isDrawingSession()
            val wasSingleTap = !didModifyModel

            touchHandler.onUp(state.editorModel)

            if (didModifyModel) {
              state.onGestureCompleted?.invoke()
            }

            val isDoubleTap = wasSingleTap &&
              hitElement != null &&
              hitElement === lastTapElement &&
              down.uptimeMillis - lastTapUptimeMillis <= viewConfiguration.doubleTapTimeoutMillis

            lastTapElement = if (wasSingleTap && !isDoubleTap) hitElement else null
            lastTapUptimeMillis = event.changes.firstOrNull()?.uptimeMillis ?: down.uptimeMillis

            if (isDoubleTap) {
              controller.onEntityDoubleTap(hitElement)
            } else if (wasSingleTap) {
              controller.onEntitySingleTap(hitElement)
            }

            break
          }

          if (canZoomCanvas) {
            // Deliberately outside didPinch: zooming is not an edit and must not mark the model dirty.
            zoomPointers = if (currentCount == 2) {
              val current = currentPressed[0].position to currentPressed[1].position
              zoomPointers?.let { controller.zoomBetween(it, current) }
              current
            } else {
              null
            }
          } else if (currentCount == 2 && previousPointerCount < 2) {
            val newPointer = event.changes.firstOrNull { it.changedToDown() } ?: currentPressed.last()
            val pointerIndex = event.changes.indexOf(newPointer).coerceIn(0, 1)
            touchHandler.onSecondPointerDown(state.editorModel, state.viewMatrix, newPointer.position.toPointF(), pointerIndex)

            // Only an edit if it found something to scale: two fingers on empty space must not arm the discard prompt.
            didPinch = touchHandler.hasActiveSession()
          } else if (currentCount == 1 && previousPointerCount == 2) {
            val released = event.changes.firstOrNull { !it.pressed && it.previousPressed }
            val releasedIndex = if (released != null) event.changes.indexOf(released).coerceIn(0, 1) else 0
            touchHandler.onSecondPointerUp(state.editorModel, state.viewMatrix, releasedIndex)
          } else if (touchHandler.hasActiveSession()) {
            val pointers = currentPressed.take(2).map { it.position.toPointF() }.toTypedArray()
            touchHandler.onMove(state.editorModel, pointers)
            state.invalidate()
          }

          val position = currentPressed.first().position
          if (inDrag) {
            controller.onDragMoved(draggedElement, touchHandler.checkTrashIntersect(state.editorModel, position.toPointF()))
          } else if (!canZoomCanvas && currentCount == 1 && !touchHandler.isDrawingSession() && (position - down.position).getDistanceSquared() > MAX_MOVE_SQUARED_BEFORE_DRAG) {
            inDrag = true
            draggedElement = touchHandler.getSelected()
            controller.onDragStarted(draggedElement)
          }

          event.changes.forEach { it.consume() }
          previousPointerCount = currentCount
        }
      } finally {
        state.isGestureActive = false

        if (inDrag) {
          controller.onDragEnded(draggedElement, droppedOnTrash)
        }
      }
    }
  }
}

private fun Offset.toPointF(): PointF = PointF(x, y)

/** Consumes nothing while waiting, so a one-finger swipe still reaches the pager. */
private suspend fun AwaitPointerEventScope.awaitSecondPointer(): Boolean {
  while (true) {
    val event = awaitPointerEvent()

    if (event.changes.any { it.isConsumed }) return false

    val pressed = event.changes.count { it.pressed }
    if (pressed == 0) return false
    if (pressed >= 2) return true
  }
}

/**
 * Owns the whole gesture while the canvas is zoomed: one finger pans, two pinch, a tap toggles the chrome, and a double
 * tap eases the image back to its fit scale. Nothing reaches the pager from here -- a swipe is a pan.
 *
 * The settle runs on [scope] rather than here, so gesture detection is back up before it finishes.
 *
 * Returns the uptime a following tap has to beat to count as a double tap, or zero when this gesture was not a tap.
 */
private suspend fun AwaitPointerEventScope.zoomGesture(
  controller: ImageController,
  down: PointerInputChange,
  lastTapUptimeMillis: Long,
  scope: CoroutineScope
): Long {
  down.consume()
  controller.imageEditorState.isGestureActive = true

  val isSecondTap = down.uptimeMillis - lastTapUptimeMillis <= viewConfiguration.doubleTapTimeoutMillis
  var isTap = true
  var zoomPointers: Pair<Offset, Offset>? = null

  try {
    while (true) {
      val event = awaitPointerEvent()
      val pressed = event.changes.filter { it.pressed }
      event.changes.forEach { it.consume() }

      if (pressed.isEmpty()) {
        break
      }

      if (pressed.size >= 2) {
        isTap = false
        val current = pressed[0].position to pressed[1].position
        zoomPointers?.let { controller.zoomBetween(it, current) }
        zoomPointers = current
        continue
      }

      // The pinch is over, so the finger left behind starts panning from wherever it is rather than from the midpoint.
      zoomPointers = null

      val change = pressed.first()
      if (isTap && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
        isTap = false
      }

      if (!isTap) {
        val pan = change.position - change.previousPosition
        controller.panBy(pan.x, pan.y)
      }
    }
  } finally {
    controller.imageEditorState.isGestureActive = false
  }

  if (!isTap) {
    return 0L
  }

  if (isSecondTap) {
    scope.launch { controller.exitZoomMode() }
    return 0L
  }

  controller.toggleChromeRevealed()
  return down.uptimeMillis
}

private fun ImageController.zoomBetween(previous: Pair<Offset, Offset>, current: Pair<Offset, Offset>) {
  val previousSpread = (previous.first - previous.second).getDistance()
  val currentSpread = (current.first - current.second).getDistance()
  if (previousSpread <= 0f || currentSpread <= 0f) return

  val previousMidpoint = (previous.first + previous.second) / 2f
  val currentMidpoint = (current.first + current.second) / 2f
  val pan = currentMidpoint - previousMidpoint

  zoomBy(
    focusX = currentMidpoint.x,
    focusY = currentMidpoint.y,
    scaleFactor = currentSpread / previousSpread,
    panX = pan.x,
    panY = pan.y
  )
}
