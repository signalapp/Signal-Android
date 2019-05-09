package org.thoughtcrime.securesms.imageeditor;

import android.graphics.Matrix;
import android.graphics.PointF;

import org.thoughtcrime.securesms.imageeditor.model.EditorElement;

/**
 * Represents an underway edit of the image.
 * <p>
 * Accepts new touch positions, new touch points, released touch points and when complete can commit the edit.
 * <p>
 * Examples of edit session implementations are, Drag, Draw, Resize:
 * <p>
 * {@link ElementDragEditSession} for dragging with a single finger.
 * {@link ElementScaleEditSession} for resize/dragging with two fingers.
 * {@link DrawingSession} for drawing with a single finger.
 */
interface EditSession {

  void movePoint(int p, PointF point);

  EditorElement getSelected();

  EditSession newPoint(Matrix newInverse, PointF point, int p);

  EditSession removePoint(Matrix newInverse, int p);

  void commit();
}
