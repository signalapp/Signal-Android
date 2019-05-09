package org.thoughtcrime.securesms.imageeditor;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.imageeditor.model.EditorElement;
import org.thoughtcrime.securesms.imageeditor.model.ThumbRenderer;

class ThumbDragEditSession extends ElementEditSession {

  @NonNull
  private final ThumbRenderer.ControlPoint controlPoint;

  private ThumbDragEditSession(@NonNull EditorElement selected, @NonNull ThumbRenderer.ControlPoint controlPoint, @NonNull Matrix inverseMatrix) {
    super(selected, inverseMatrix);
    this.controlPoint = controlPoint;
  }

  static EditSession startDrag(@NonNull EditorElement selected, @NonNull Matrix inverseViewModelMatrix, @NonNull ThumbRenderer.ControlPoint controlPoint, @NonNull PointF point) {
    if (!selected.getFlags().isEditable()) return null;

    ElementEditSession elementDragEditSession = new ThumbDragEditSession(selected, controlPoint, inverseViewModelMatrix);
    elementDragEditSession.setScreenStartPoint(0, point);
    elementDragEditSession.setScreenEndPoint(0, point);
    return elementDragEditSession;
  }

  @Override
  public void movePoint(int p, @NonNull PointF point) {
    setScreenEndPoint(p, point);

    Matrix editorMatrix = selected.getEditorMatrix();

    editorMatrix.reset();

    float x = controlPoint.opposite().getX();
    float y = controlPoint.opposite().getY();

    editorMatrix.postTranslate(-x, -y);

    boolean aspectLocked = selected.getFlags().isAspectLocked();

    float defaultScale = aspectLocked ? 2 : 1;

    float scaleX = controlPoint.isVerticalCenter()   ? defaultScale : (endPointElement[0].x - x) / (startPointElement[0].x - x);
    float scaleY = controlPoint.isHorizontalCenter() ? defaultScale : (endPointElement[0].y - y) / (startPointElement[0].y - y);

    if (aspectLocked) {
      float minScale = Math.min(scaleX, scaleY);
      editorMatrix.postScale(minScale, minScale);
    } else {
      editorMatrix.postScale(scaleX, scaleY);
    }

    editorMatrix.postTranslate(x, y);
  }

  @Override
  public EditSession newPoint(Matrix newInverse, PointF point, int p) {
    return null;
  }

  @Override
  public EditSession removePoint(Matrix newInverse, int p) {
    return null;
  }
}
