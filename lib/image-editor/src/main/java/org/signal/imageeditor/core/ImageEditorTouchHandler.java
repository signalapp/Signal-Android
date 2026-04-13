/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.imageeditor.core;

import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.imageeditor.core.model.EditorElement;
import org.signal.imageeditor.core.model.EditorModel;
import org.signal.imageeditor.core.model.ThumbRenderer;
import org.signal.imageeditor.core.renderers.BezierDrawingRenderer;

/**
 * Public facade for touch handling on an {@link EditorModel}.
 * <p>
 * Encapsulates the {@link EditSession} creation and lifecycle so that callers outside
 * this package (e.g. a Compose component) can drive the editor without accessing
 * package-private classes directly.
 * <p>
 * Usage: call the on* methods in order as pointer events arrive. The handler manages
 * edit session state internally.
 */
public final class ImageEditorTouchHandler {

  private boolean drawing;
  private boolean blur;
  private int       drawColor     = 0xff000000;
  private float     drawThickness = 0.02f;
  @NonNull
  private Paint.Cap drawCap       = Paint.Cap.ROUND;

  @Nullable private EditSession editSession;
  private boolean moreThanOnePointerUsedInSession;

  /** Configures whether the next gesture should create a drawing session if no element is hit. */
  public void setDrawing(boolean drawing, boolean blur) {
    this.drawing = drawing;
    this.blur    = blur;
  }

  /** Sets the brush parameters used when creating new drawing sessions. */
  public void setDrawingBrush(int color, float thickness, @NonNull Paint.Cap cap) {
    this.drawColor     = color;
    this.drawThickness = thickness;
    this.drawCap       = cap;
  }

  /** Begins a new gesture. Creates either a move/resize, thumb drag, or drawing session. */
  @Nullable
  public EditorElement onDown(@NonNull EditorModel model, @NonNull Matrix viewMatrix, @NonNull PointF point) {
    Matrix        inverse  = new Matrix();
    EditorElement selected = model.findElementAtPoint(point, viewMatrix, inverse);

    moreThanOnePointerUsedInSession = false;
    model.pushUndoPoint();
    editSession = startEdit(model, viewMatrix, inverse, point, selected);

    return editSession != null ? editSession.getSelected() : null;
  }

  /** Feeds pointer positions to the active session. Call for every move event. */
  public void onMove(@NonNull EditorModel model, @NonNull PointF[] pointers) {
    if (editSession == null) return;

    int pointerCount = Math.min(2, pointers.length);
    for (int p = 0; p < pointerCount; p++) {
      editSession.movePoint(p, pointers[p]);
    }
    model.moving(editSession.getSelected());
  }

  /** Transitions a single-finger session to a two-finger session (e.g. pinch-to-zoom). */
  public void onSecondPointerDown(@NonNull EditorModel model, @NonNull Matrix viewMatrix, @NonNull PointF newPointerPoint, int pointerIndex) {
    if (editSession == null) return;

    moreThanOnePointerUsedInSession = true;
    editSession.commit();
    model.pushUndoPoint();

    Matrix newInverse = model.findElementInverseMatrix(editSession.getSelected(), viewMatrix);
    if (newInverse != null) {
      editSession = editSession.newPoint(newInverse, newPointerPoint, pointerIndex);
    } else {
      editSession = null;
    }

    if (editSession == null) {
      model.dragDropRelease();
    }
  }

  /** Transitions a two-finger session back to single-finger when one pointer lifts. */
  public void onSecondPointerUp(@NonNull EditorModel model, @NonNull Matrix viewMatrix, int releasedIndex) {
    if (editSession == null) return;

    editSession.commit();
    model.pushUndoPoint();
    model.dragDropRelease();

    Matrix newInverse = model.findElementInverseMatrix(editSession.getSelected(), viewMatrix);
    if (newInverse != null) {
      editSession = editSession.removePoint(newInverse, releasedIndex);
    } else {
      editSession = null;
    }
  }

  /** Ends the current gesture: commits the session and calls {@link EditorModel#postEdit}. */
  public void onUp(@NonNull EditorModel model) {
    if (editSession != null) {
      editSession.commit();
      model.dragDropRelease();
      editSession = null;
    }
    model.postEdit(moreThanOnePointerUsedInSession);
  }

  public void cancel() {
    editSession = null;
  }

  public boolean hasActiveSession() {
    return editSession != null;
  }

  @Nullable
  public EditorElement getSelected() {
    return editSession != null ? editSession.getSelected() : null;
  }

  private @Nullable EditSession startEdit(
    @NonNull EditorModel model,
    @NonNull Matrix viewMatrix,
    @NonNull Matrix inverse,
    @NonNull PointF point,
    @Nullable EditorElement selected
  ) {
    EditSession session = startMoveAndResizeSession(model, viewMatrix, inverse, point, selected);
    if (session == null && drawing) {
      return startDrawingSession(model, viewMatrix, point);
    }
    return session;
  }

  private @Nullable EditSession startDrawingSession(@NonNull EditorModel model, @NonNull Matrix viewMatrix, @NonNull PointF point) {
    BezierDrawingRenderer renderer = new BezierDrawingRenderer(drawColor, drawThickness * Bounds.FULL_BOUNDS.width(), drawCap, model.findCropRelativeToRoot());
    EditorElement         element  = new EditorElement(renderer, blur ? EditorModel.Z_MASK : EditorModel.Z_DRAWING);

    model.addElementCentered(element, 1);

    Matrix elementInverseMatrix = model.findElementInverseMatrix(element, viewMatrix);

    return DrawingSession.start(element, renderer, elementInverseMatrix, point);
  }

  private static @Nullable EditSession startMoveAndResizeSession(
    @NonNull EditorModel model,
    @NonNull Matrix viewMatrix,
    @NonNull Matrix inverse,
    @NonNull PointF point,
    @Nullable EditorElement selected
  ) {
    if (selected == null) return null;

    if (selected.getRenderer() instanceof ThumbRenderer) {
      ThumbRenderer thumb = (ThumbRenderer) selected.getRenderer();

      EditorElement thumbControlledElement = model.findById(thumb.getElementToControl());
      if (thumbControlledElement == null) return null;

      EditorElement thumbsParent = model.getRoot().findParent(selected);
      if (thumbsParent == null) return null;

      Matrix thumbContainerRelativeMatrix = model.findRelativeMatrix(thumbsParent, thumbControlledElement);
      if (thumbContainerRelativeMatrix == null) return null;

      selected = thumbControlledElement;

      Matrix elementInverseMatrix = model.findElementInverseMatrix(selected, viewMatrix);
      if (elementInverseMatrix != null) {
        return ThumbDragEditSession.startDrag(selected, elementInverseMatrix, thumbContainerRelativeMatrix, thumb.getControlPoint(), point);
      } else {
        return null;
      }
    }

    return ElementDragEditSession.startDrag(selected, inverse, point);
  }
}
