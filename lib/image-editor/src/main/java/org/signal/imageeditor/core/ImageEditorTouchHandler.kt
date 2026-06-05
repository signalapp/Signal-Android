/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.imageeditor.core

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.model.ThumbRenderer
import org.signal.imageeditor.core.renderers.BezierDrawingRenderer

/**
 * Public facade for touch handling on an [EditorModel].
 *
 * Encapsulates the [EditSession] creation and lifecycle so that callers outside
 * this package (e.g. a Compose component) can drive the editor without accessing
 * package-private classes directly.
 *
 * Usage: call the on* methods in order as pointer events arrive. The handler manages
 * edit session state internally.
 */
class ImageEditorTouchHandler {

  private var drawing: Boolean = false
  private var blur: Boolean = false
  private var drawColor: Int = 0xff000000.toInt()
  private var drawThickness: Float = 0.02f
  private var drawCap: Paint.Cap = Paint.Cap.ROUND

  private var editSession: EditSession? = null
  private var moreThanOnePointerUsedInSession: Boolean = false

  /** Configures whether the next gesture should create a drawing session if no element is hit. */
  fun setDrawing(drawing: Boolean, blur: Boolean) {
    this.drawing = drawing
    this.blur = blur
  }

  /** Sets the brush parameters used when creating new drawing sessions. */
  fun setDrawingBrush(color: Int, thickness: Float, cap: Paint.Cap) {
    drawColor = color
    drawThickness = thickness
    drawCap = cap
  }

  /** Begins a new gesture. Creates either a move/resize, thumb drag, or drawing session. */
  fun onDown(model: EditorModel, viewMatrix: Matrix, point: PointF): EditorElement? {
    val inverse = Matrix()
    val selected = model.findElementAtPoint(point, viewMatrix, inverse)

    moreThanOnePointerUsedInSession = false
    model.pushUndoPoint()
    editSession = startEdit(model, viewMatrix, inverse, point, selected)

    return editSession?.selected
  }

  /** Feeds pointer positions to the active session. Call for every move event. */
  fun onMove(model: EditorModel, pointers: Array<PointF>) {
    val currentEditSession = editSession ?: return

    val pointerCount = minOf(2, pointers.size)
    for (p in 0 until pointerCount) {
      currentEditSession.movePoint(p, pointers[p])
    }
    model.moving(currentEditSession.selected)
  }

  /** Transitions a single-finger session to a two-finger session (e.g. pinch-to-zoom). */
  fun onSecondPointerDown(model: EditorModel, viewMatrix: Matrix, newPointerPoint: PointF, pointerIndex: Int) {
    val currentEditSession = editSession ?: return

    moreThanOnePointerUsedInSession = true
    currentEditSession.commit()
    model.pushUndoPoint()

    val newInverse = model.findElementInverseMatrix(currentEditSession.selected, viewMatrix)
    editSession = if (newInverse != null) {
      currentEditSession.newPoint(newInverse, newPointerPoint, pointerIndex)
    } else {
      null
    }

    if (editSession == null) {
      model.dragDropRelease()
    }
  }

  /** Transitions a two-finger session back to single-finger when one pointer lifts. */
  fun onSecondPointerUp(model: EditorModel, viewMatrix: Matrix, releasedIndex: Int) {
    val currentEditSession = editSession ?: return

    currentEditSession.commit()
    model.pushUndoPoint()
    model.dragDropRelease()

    val newInverse = model.findElementInverseMatrix(currentEditSession.selected, viewMatrix)
    editSession = if (newInverse != null) {
      currentEditSession.removePoint(newInverse, releasedIndex)
    } else {
      null
    }
  }

  /** Ends the current gesture: commits the session and calls [EditorModel.postEdit]. */
  fun onUp(model: EditorModel) {
    editSession?.let {
      it.commit()
      model.dragDropRelease()
      editSession = null
    }
    model.postEdit(moreThanOnePointerUsedInSession)
  }

  fun cancel() {
    editSession = null
  }

  fun hasActiveSession(): Boolean {
    return editSession != null
  }

  fun getSelected(): EditorElement? {
    return editSession?.selected
  }

  private fun startEdit(
    model: EditorModel,
    viewMatrix: Matrix,
    inverse: Matrix,
    point: PointF,
    selected: EditorElement?
  ): EditSession? {
    val session = startMoveAndResizeSession(model, viewMatrix, inverse, point, selected)
    if (session == null && drawing) {
      return startDrawingSession(model, viewMatrix, point)
    }
    return session
  }

  private fun startDrawingSession(model: EditorModel, viewMatrix: Matrix, point: PointF): EditSession {
    val renderer = BezierDrawingRenderer(
      drawColor,
      drawThickness * Bounds.FULL_BOUNDS.width(),
      drawCap,
      model.findCropRelativeToRoot()
    )
    val element = EditorElement(renderer, if (blur) EditorModel.Z_MASK else EditorModel.Z_DRAWING)

    model.addElementCentered(element, 1f)

    val elementInverseMatrix = model.findElementInverseMatrix(element, viewMatrix)

    return DrawingSession.start(element, renderer, elementInverseMatrix, point)
  }

  private companion object {
    fun startMoveAndResizeSession(
      model: EditorModel,
      viewMatrix: Matrix,
      inverse: Matrix,
      point: PointF,
      selected: EditorElement?
    ): EditSession? {
      if (selected == null) return null

      if (selected.renderer is ThumbRenderer) {
        val thumb = selected.renderer as ThumbRenderer

        val thumbControlledElement = model.findById(thumb.elementToControl) ?: return null
        val thumbsParent = model.root.findParent(selected) ?: return null
        val thumbContainerRelativeMatrix = model.findRelativeMatrix(thumbsParent, thumbControlledElement) ?: return null

        val elementInverseMatrix = model.findElementInverseMatrix(thumbControlledElement, viewMatrix)
        return if (elementInverseMatrix != null) {
          ThumbDragEditSession.startDrag(
            thumbControlledElement,
            elementInverseMatrix,
            thumbContainerRelativeMatrix,
            thumb.controlPoint,
            point
          )
        } else {
          null
        }
      }

      return ElementDragEditSession.startDrag(selected, inverse, point)
    }
  }
}
