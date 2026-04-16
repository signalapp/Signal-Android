/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.signal.imageeditor.core.ImageEditorTouchHandler

@Composable
fun ImageEditor(
  controller: EditorController.Image,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val state = controller.imageEditorState

  DisposableEffect(state) {
    state.attach()
    onDispose { state.detach() }
  }

  Box(modifier = modifier) {
    Canvas(
      modifier = Modifier
        .matchParentSize()
        .clipToBounds()
        .onSizeChanged { state.updateViewMatrix(it.width.toFloat(), it.height.toFloat()) }
        .imageEditorPointerInput(state, controller)
    ) {
      state.revision

      val nativeCanvas = drawContext.canvas.nativeCanvas
      val rendererContext = state.getOrCreateRendererContext(context, nativeCanvas)
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
private fun HiddenTextInput(controller: EditorController.Image) {
  var text by remember { mutableStateOf(TextFieldValue("")) }
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

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  DisposableEffect(Unit) {
    onDispose { keyboardController?.hide() }
  }
}

private fun Modifier.imageEditorPointerInput(state: ImageEditorState, controller: EditorController.Image): Modifier {
  return this.pointerInput(controller, controller.textEditingElement) {
    val touchHandler = ImageEditorTouchHandler()

    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = true)

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
        controller.onEntityTapped(hitElement)
      }

      // In NONE mode with nothing hit, let the pager handle the gesture
      if (controller.mode == EditorController.Image.Mode.NONE && !touchHandler.hasActiveSession()) {
        return@awaitEachGesture
      }

      down.consume()

      var previousPointerCount = 1

      while (true) {
        val event = awaitPointerEvent()
        val currentPressed = event.changes.filter { it.pressed }
        val currentCount = currentPressed.size

        if (currentCount == 0) {
          event.changes.forEach { it.consume() }
          touchHandler.onUp(state.editorModel)
          state.onGestureCompleted?.invoke()
          break
        }

        if (currentCount == 2 && previousPointerCount < 2) {
          val newPointer = event.changes.firstOrNull { it.changedToDown() } ?: currentPressed.last()
          val pointerIndex = event.changes.indexOf(newPointer).coerceIn(0, 1)
          touchHandler.onSecondPointerDown(state.editorModel, state.viewMatrix, newPointer.position.toPointF(), pointerIndex)
        } else if (currentCount == 1 && previousPointerCount == 2) {
          val released = event.changes.firstOrNull { !it.pressed && it.previousPressed }
          val releasedIndex = if (released != null) event.changes.indexOf(released).coerceIn(0, 1) else 0
          touchHandler.onSecondPointerUp(state.editorModel, state.viewMatrix, releasedIndex)
        } else if (touchHandler.hasActiveSession()) {
          val pointers = currentPressed.take(2).map { it.position.toPointF() }.toTypedArray()
          touchHandler.onMove(state.editorModel, pointers)
          state.invalidate()
        }

        event.changes.forEach { it.consume() }
        previousPointerCount = currentCount
      }
    }
  }
}

private fun Offset.toPointF(): PointF = PointF(x, y)
