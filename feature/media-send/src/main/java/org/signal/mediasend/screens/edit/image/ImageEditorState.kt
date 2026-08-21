/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.signal.imageeditor.core.Bounds
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.RendererContext
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.screens.edit.ChromeInsets

/** In model units. */
private const val MIN_CONTENT_VIEW_PORT = 100f

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 8f

/** How long the canvas takes to ease back to its fit, rather than snapping there. */
private const val ZOOM_SETTLE_DURATION_MILLIS = 250

/**
 * Compose-observable wrapper around [EditorModel].
 *
 * Hooks into the model's invalidation callback so that a revision counter (read during the
 * Canvas draw phase) triggers redraws whenever the model changes. This gives us unidirectional
 * flow: mutations go into the model, the composable only reads and renders.
 */
@Stable
internal class ImageEditorState(
  val editorModel: EditorModel
) {

  var revision: Long by mutableLongStateOf(0L)
    private set

  var undoAvailable: Boolean by mutableStateOf(false)
    private set

  var redoAvailable: Boolean by mutableStateOf(false)
    private set

  /** True while the user is actively manipulating the image: drawing, moving, scaling, or rotating. */
  var isGestureActive: Boolean by mutableStateOf(false)
    internal set

  /** Whether the canvas is scaled past its fit. */
  var isZoomed: Boolean by mutableStateOf(false)
    private set

  var textEditingElement: EditorElement? = null
  var isDrawing: Boolean = false
  var isBlur: Boolean = false
  var drawColor: Int by mutableIntStateOf(0xff000000.toInt())
  var drawThickness: Float = 0.02f
  var drawCap: Paint.Cap = Paint.Cap.ROUND
  var onGestureCompleted: (() -> Unit)? = null

  val viewMatrix: Matrix = Matrix()
  val visibleViewPort: RectF = RectF(Bounds.LEFT, Bounds.TOP, Bounds.RIGHT, Bounds.BOTTOM)

  private val viewPort: RectF = RectF(Bounds.LEFT, Bounds.TOP, Bounds.RIGHT, Bounds.BOTTOM)
  private val contentViewPort: RectF = RectF(Bounds.LEFT, Bounds.TOP, Bounds.RIGHT, Bounds.BOTTOM)
  private val screen: RectF = RectF()

  private val fitMatrix: Matrix = Matrix()
  private var zoomScale: Float = 1f
  private var zoomTranslateX: Float = 0f
  private var zoomTranslateY: Float = 0f

  private var canvasWidth: Float = 0f
  private var canvasHeight: Float = 0f
  private var contentInsets: ChromeInsets = ChromeInsets()

  private var rendererContext: RendererContext? = null
  private var attachCount: Int = 0

  private val rendererReady = RendererContext.Ready { renderer: Renderer, cropMatrix: Matrix?, size: Point? ->
    editorModel.onReady(renderer, cropMatrix, size)
    revision++
  }

  private val rendererInvalidate = RendererContext.Invalidate { _: Renderer ->
    revision++
  }

  val typefaceProvider = RendererContext.TypefaceProvider { _: Context, _: Renderer, _: RendererContext.Invalidate ->
    Typeface.DEFAULT
  }

  /** Manually triggers a Canvas redraw. Call after touch moves that modify the model directly. */
  fun invalidate() {
    revision++
  }

  /**
   * Hooks into the [EditorModel]'s invalidation and undo/redo callbacks. Call in [DisposableEffect].
   *
   * Reference counted: removing media shifts the surviving items down a pager slot, and Compose applies the new
   * slot's subcomposition before disposing the old one, so a state can be attached by its new page while its old
   * page is still to be torn down. Without the count that teardown would unhook a state that is still on screen,
   * leaving the model unable to trigger a redraw for anything it drives itself -- crop, rotate, flip, undo/redo.
   */
  fun attach() {
    attachCount++
    editorModel.setInvalidate { revision++ }
    editorModel.setUndoRedoStackListener { undo, redo ->
      undoAvailable = undo
      redoAvailable = redo
    }
  }

  /** Unhooks from the [EditorModel] once the last holder has let go. Call in [DisposableEffect]'s onDispose. */
  fun detach() {
    attachCount = (attachCount - 1).coerceAtLeast(0)
    if (attachCount > 0) return

    editorModel.setInvalidate(null)
    editorModel.setUndoRedoStackListener(null)
  }

  fun setCanvasSize(width: Float, height: Float) {
    if (width == canvasWidth && height == canvasHeight) return

    canvasWidth = width
    canvasHeight = height

    clearZoom()
    updateViewMatrix()
  }

  /** Layered onto [viewMatrix] after the fit rather than pushed into the model, so it is not undoable or exported. */
  fun zoomBy(focusX: Float, focusY: Float, scaleFactor: Float, panX: Float, panY: Float) {
    val scaled = (zoomScale * scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val applied = scaled / zoomScale

    // Pin what is under the midpoint of the fingers while the scale changes, then track the fingers.
    zoomTranslateX = focusX - (focusX - zoomTranslateX) * applied + panX
    zoomTranslateY = focusY - (focusY - zoomTranslateY) * applied + panY
    zoomScale = scaled

    constrainZoom()
    applyZoom()
  }

  /** Drags the zoomed canvas under the finger. A no-op at the fit scale, where there is nothing to pan to. */
  fun panBy(panX: Float, panY: Float) {
    zoomBy(focusX = 0f, focusY = 0f, scaleFactor = 1f, panX = panX, panY = panY)
  }

  /**
   * Eases the canvas back to its fit and leaves the zoom cleared. Scale and translation are driven off one fraction,
   * which is what keeps every frame inside [constrainZoom]'s bounds without having to re-clamp along the way.
   */
  suspend fun animateZoomToFit() {
    val fromScale = zoomScale
    val fromTranslateX = zoomTranslateX
    val fromTranslateY = zoomTranslateY

    animate(
      initialValue = 1f,
      targetValue = 0f,
      animationSpec = tween(durationMillis = ZOOM_SETTLE_DURATION_MILLIS, easing = FastOutSlowInEasing)
    ) { fraction, _ ->
      zoomScale = MIN_ZOOM + (fromScale - MIN_ZOOM) * fraction
      zoomTranslateX = fromTranslateX * fraction
      zoomTranslateY = fromTranslateY * fraction
      applyZoom()
    }

    clearZoom()
  }

  fun clearZoom() {
    if (zoomScale == 1f && zoomTranslateX == 0f && zoomTranslateY == 0f) return

    zoomScale = 1f
    zoomTranslateX = 0f
    zoomTranslateY = 0f
    applyZoom()
  }

  private fun constrainZoom() {
    zoomTranslateX = zoomTranslateX.coerceIn(canvasWidth - canvasWidth * zoomScale, 0f)
    zoomTranslateY = zoomTranslateY.coerceIn(canvasHeight - canvasHeight * zoomScale, 0f)
  }

  private fun applyZoom() {
    viewMatrix.set(fitMatrix)
    viewMatrix.postScale(zoomScale, zoomScale)
    viewMatrix.postTranslate(zoomTranslateX, zoomTranslateY)
    revision++

    isZoomed = zoomScale > MIN_ZOOM
  }

  fun setContentInsets(insets: ChromeInsets) {
    if (insets == contentInsets) return

    contentInsets = insets
    updateViewMatrix()
  }

  /**
   * Unlike the view-based editor this was ported from, the rect handed to the model is inset by [contentInsets] while
   * the matrix still spans the full canvas, so transformed content can still reach the screen edges.
   */
  private fun updateViewMatrix() {
    if (canvasWidth <= 0f || canvasHeight <= 0f) return

    screen.set(0f, 0f, canvasWidth, canvasHeight)
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

    // Viewport and screen share an aspect ratio by construction, so one scale covers both axes.
    val pixelsToModel = if (screen.width() > 0f) visibleViewPort.width() / screen.width() else 0f
    contentViewPort.set(
      visibleViewPort.left + contentInsets.left * pixelsToModel,
      visibleViewPort.top + contentInsets.top * pixelsToModel,
      visibleViewPort.right - contentInsets.right * pixelsToModel,
      visibleViewPort.bottom - contentInsets.bottom * pixelsToModel
    )

    // Chrome plus keyboard can exceed the screen on a short device.
    if (contentViewPort.width() < MIN_CONTENT_VIEW_PORT || contentViewPort.height() < MIN_CONTENT_VIEW_PORT) {
      contentViewPort.set(visibleViewPort)
    }

    editorModel.setVisibleViewPort(contentViewPort)

    fitMatrix.set(viewMatrix)
    constrainZoom()
    applyZoom()
  }

  /** Returns a cached [RendererContext], recreating it only when the canvas instance changes. */
  fun getOrCreateRendererContext(context: Context, canvas: Canvas): RendererContext {
    val current = rendererContext
    if (current != null && current.canvas === canvas) return current
    return RendererContext(context, canvas, rendererReady, rendererInvalidate, typefaceProvider).also {
      rendererContext = it
    }
  }
}
