package org.thoughtcrime.securesms.imageeditor;

import android.graphics.Matrix;
import android.graphics.PointF;

import androidx.annotation.NonNull;

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

    float dx = endPointElement[0].x - startPointElement[0].x;
    float dy = endPointElement[0].y - startPointElement[0].y;

    float xEnd = controlPoint.getX() + dx;
    float yEnd = controlPoint.getY() + dy;

    boolean aspectLocked = selected.getFlags().isAspectLocked() && !controlPoint.isCenter();

    float defaultScale = aspectLocked ? 2 : 1;

    float scaleX = controlPoint.isVerticalCenter()   ? defaultScale : (xEnd - x) / (controlPoint.getX() - x);
    float scaleY = controlPoint.isHorizontalCenter() ? defaultScale : (yEnd - y) / (controlPoint.getY() - y);

    scale(editorMatrix, aspectLocked, scaleX, scaleY, controlPoint.opposite());
  }

  private void scale(Matrix editorMatrix, boolean aspectLocked, float scaleX, float scaleY, ThumbRenderer.ControlPoint around) {
    float x = around.getX();
    float y = around.getY();
    editorMatrix.postTranslate(-x, -y);
    if (aspectLocked) {
      float minScale = Math.min(scaleX, scaleY);
      editorMatrix.postScale(minScale, minScale);
    } else {
      editorMatrix.postScale(scaleX, scaleY);
    }
    editorMatrix.postTranslate(x, y);
  }

  @Override
  public EditSession newPoint(@NonNull Matrix newInverse, @NonNull PointF point, int p) {
    return null;
  }

  @Override
  public EditSession removePoint(@NonNull Matrix newInverse, int p) {
    return null;
  }
}