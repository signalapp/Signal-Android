/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.signal.imageeditor.core.Bounds
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.RendererContext
import org.signal.imageeditor.core.model.EditorModel

/**
 * Compose-observable wrapper around [EditorModel].
 *
 * Hooks into the model's invalidation callback so that a revision counter (read during the
 * Canvas draw phase) triggers redraws whenever the model changes. This gives us unidirectional
 * flow: mutations go into the model, the composable only reads and renders.
 */
@Stable
class ImageEditorState(
  val editorModel: EditorModel
) {

  var revision: Long by mutableLongStateOf(0L)
    private set

  var undoAvailable: Boolean by mutableStateOf(false)
    private set

  var redoAvailable: Boolean by mutableStateOf(false)
    private set

  var isDrawing: Boolean = false
  var isBlur: Boolean = false
  var drawColor: Int = 0xff000000.toInt()
  var drawThickness: Float = 0.02f
  var drawCap: Paint.Cap = Paint.Cap.ROUND
  var onGestureCompleted: (() -> Unit)? = null

  val viewMatrix: Matrix = Matrix()
  val visibleViewPort: RectF = RectF(Bounds.LEFT, Bounds.TOP, Bounds.RIGHT, Bounds.BOTTOM)

  private val viewPort: RectF = RectF(Bounds.LEFT, Bounds.TOP, Bounds.RIGHT, Bounds.BOTTOM)
  private val screen: RectF = RectF()

  private var rendererContext: RendererContext? = null

  private val rendererReady = RendererContext.Ready { renderer: Renderer, cropMatrix: Matrix?, size: Point? ->
    editorModel.onReady(renderer, cropMatrix, size)
    revision++
  }

  private val rendererInvalidate = RendererContext.Invalidate { _: Renderer ->
    revision++
  }

  private val defaultTypefaceProvider = RendererContext.TypefaceProvider { _: Context, _: Renderer, _: RendererContext.Invalidate ->
    Typeface.DEFAULT
  }

  /** Manually triggers a Canvas redraw. Call after touch moves that modify the model directly. */
  fun invalidate() {
    revision++
  }

  /** Hooks into the [EditorModel]'s invalidation and undo/redo callbacks. Call in [DisposableEffect]. */
  fun attach() {
    editorModel.setInvalidate { revision++ }
    editorModel.setUndoRedoStackListener { undo, redo ->
      undoAvailable = undo
      redoAvailable = redo
    }
  }

  /** Unhooks from the [EditorModel]. Call in [DisposableEffect]'s onDispose. */
  fun detach() {
    editorModel.setInvalidate(null)
    editorModel.setUndoRedoStackListener(null)
  }

  /** Recomputes the view matrix to map the editor's coordinate space to the given pixel dimensions. */
  fun updateViewMatrix(width: Float, height: Float) {
    screen.set(0f, 0f, width, height)
    viewMatrix.setRectToRect(viewPort, screen, Matrix.ScaleToFit.FILL)

    val values = FloatArray(9)
    viewMatrix.getValues(values)
    val scale = values[0] / values[4]

    val tempViewPort = RectF(Bounds.LEFT, Bounds.TOP, Bounds.RIGHT, Bounds.BOTTOM)
    if (scale < 1) {
      tempViewPort.top /= scale
      tempViewPort.bottom /= scale
    } else {
      tempViewPort.left *= scale
      tempViewPort.right *= scale
    }

    visibleViewPort.set(tempViewPort)
    viewMatrix.setRectToRect(visibleViewPort, screen, Matrix.ScaleToFit.CENTER)
    editorModel.setVisibleViewPort(visibleViewPort)
    revision++
  }

  /** Returns a cached [RendererContext], recreating it only when the canvas instance changes. */
  fun getOrCreateRendererContext(context: Context, canvas: Canvas): RendererContext {
    val current = rendererContext
    if (current != null && current.canvas === canvas) return current
    return RendererContext(context, canvas, rendererReady, rendererInvalidate, defaultTypefaceProvider).also {
      rendererContext = it
    }
  }
}
