/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import org.signal.imageeditor.core.ImageEditorTouchHandler

@Composable
fun ImageEditor(
  state: ImageEditorState,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  DisposableEffect(state) {
    state.attach()
    onDispose { state.detach() }
  }

  Canvas(
    modifier = modifier
      .clipToBounds()
      .onSizeChanged { state.updateViewMatrix(it.width.toFloat(), it.height.toFloat()) }
      .imageEditorPointerInput(state)
  ) {
    state.revision

    val nativeCanvas = drawContext.canvas.nativeCanvas
    val rendererContext = state.getOrCreateRendererContext(context, nativeCanvas)
    rendererContext.save()
    try {
      rendererContext.canvasMatrix.initial(state.viewMatrix)
      state.editorModel.draw(rendererContext, null)
    } finally {
      rendererContext.restore()
    }
  }
}

private fun Modifier.imageEditorPointerInput(state: ImageEditorState): Modifier {
  return this.pointerInput(state) {
    val touchHandler = ImageEditorTouchHandler()

    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = true)
      down.consume()
      touchHandler.setDrawing(state.isDrawing, state.isBlur)
      touchHandler.setDrawingBrush(state.drawColor, state.drawThickness, state.drawCap)
      touchHandler.onDown(state.editorModel, state.viewMatrix, down.position.toPointF())

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
