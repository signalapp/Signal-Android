package org.signal.imageeditor.core;

import android.graphics.Matrix;
import android.graphics.PointF;

import androidx.annotation.NonNull;

import org.signal.imageeditor.core.model.EditorElement;
import org.signal.imageeditor.core.model.ThumbRenderer;

class ThumbDragEditSession extends ElementEditSession {

  private final PointF  oppositeControlPoint                = new PointF();
  private final float[] oppositeControlPointOnControlParent = new float[2];
  private final float[] oppositeControlPointOnElement       = new float[2];

  @NonNull
  private final          ThumbRenderer.ControlPoint controlPoint;
  @NonNull private final Matrix                     thumbContainerRelativeMatrix;

  private ThumbDragEditSession(@NonNull EditorElement selected,
                               @NonNull ThumbRenderer.ControlPoint controlPoint,
                               @NonNull Matrix inverseMatrix,
                               @NonNull Matrix thumbContainerRelativeMatrix)
  {
    super(selected, inverseMatrix);
    this.controlPoint                 = controlPoint;
    this.thumbContainerRelativeMatrix = thumbContainerRelativeMatrix;
  }

  static EditSession startDrag(@NonNull EditorElement selected,
                               @NonNull Matrix inverseViewModelMatrix,
                               @NonNull Matrix thumbContainerRelativeMatrix,
                               @NonNull ThumbRenderer.ControlPoint controlPoint,
                               @NonNull PointF point)
  {
    if (!selected.getFlags().isEditable()) return null;

    ElementEditSession elementDragEditSession = new ThumbDragEditSession(selected, controlPoint, inverseViewModelMatrix, thumbContainerRelativeMatrix);
    elementDragEditSession.setScreenStartPoint(0, point);
    elementDragEditSession.setScreenEndPoint(0, point);
    return elementDragEditSession;
  }

  @Override
  public void movePoint(int p, @NonNull PointF point) {
    setScreenEndPoint(p, point);

    Matrix editorMatrix = selected.getEditorMatrix();

    editorMatrix.reset();

    // Think of this process as a pinch to zoom/rotate, one finger being on the control point being manipulated, and the other on its opposite.
    // Even if the opposite thumb doesn't exist on the tree, the position it would be at gives the virtual second finger position for the pinch.

    // The opposite control point needs an additional mapping to put it in to the same coordinate system as the dragged thumb
    oppositeControlPointOnControlParent[0] = controlPoint.opposite().getX();
    oppositeControlPointOnControlParent[1] = controlPoint.opposite().getY();
    thumbContainerRelativeMatrix.mapPoints(oppositeControlPointOnElement, oppositeControlPointOnControlParent);
    float x = oppositeControlPointOnElement[0];
    float y = oppositeControlPointOnElement[1];
    oppositeControlPoint.set(x, y);

    float dx = endPointElement[0].x - startPointElement[0].x;
    float dy = endPointElement[0].y - startPointElement[0].y;

    float xEnd = controlPoint.getX() + dx;
    float yEnd = controlPoint.getY() + dy;

    if (controlPoint.isScaleAndRotateThumb()) {
      float scale = findScale(oppositeControlPoint, startPointElement[0], endPointElement[0]);
      editorMatrix.postTranslate(-oppositeControlPoint.x, -oppositeControlPoint.y);
      editorMatrix.postScale(scale, scale);
      double angle = angle(endPointElement[0], oppositeControlPoint) - angle(startPointElement[0], oppositeControlPoint);
      rotate(editorMatrix, angle);
      editorMatrix.postTranslate(oppositeControlPoint.x, oppositeControlPoint.y);
    } else {
      // 8 point controls, where edges scale in just one dimension and corners scale in both, optionally fixed aspect ratio
      boolean aspectLocked = selected.getFlags().isAspectLocked() && !controlPoint.isCenter();
      float   defaultScale = aspectLocked ? 2 : 1;
      float   scaleX       = controlPoint.isVerticalCenter() ? defaultScale : (xEnd - x) / (controlPoint.getX() - x);
      float   scaleY       = controlPoint.isHorizontalCenter() ? defaultScale : (yEnd - y) / (controlPoint.getY() - y);

      scale(editorMatrix, aspectLocked, scaleX, scaleY, controlPoint.opposite());
    }
  }

  private static void scale(Matrix editorMatrix, boolean aspectLocked, float scaleX, float scaleY, @NonNull ThumbRenderer.ControlPoint around) {
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

  private static void rotate(Matrix editorMatrix, double angle) {
    editorMatrix.postRotate((float) Math.toDegrees(angle));
  }

  private static double angle(@NonNull PointF a, @NonNull PointF b) {
    return Math.atan2(a.y - b.y, a.x - b.x);
  }

  @Override
  public EditSession newPoint(@NonNull Matrix newInverse, @NonNull PointF point, int p) {
    return null;
  }

  @Override
  public EditSession removePoint(@NonNull Matrix newInverse, int p) {
    return null;
  }

  /**
   * Find relative distance between an old and new Point relative to an anchor.
   * <p>
   * <pre>
   * |to - anchor| / |from - anchor|
   * </pre>
   *
   * @param anchor Fixed point.
   * @param from   Starting point.
   * @param to     Ending point.
   * @return Scale required to scale a line anchor->from to reach the to point from anchor.
   */
  private static float findScale(@NonNull PointF anchor, @NonNull PointF from, @NonNull PointF to) {
    float originalD2 = getDistanceSquared(from, anchor);
    float newD2      = getDistanceSquared(to, anchor);
    return (float) Math.sqrt(newD2 / originalD2);
  }

  /**
   * Distance between two points squared.
   */
  private static float getDistanceSquared(@NonNull PointF a, @NonNull PointF b) {
    float dx = a.x - b.x;
    float dy = a.y - b.y;
    return dx * dx + dy * dy;
  }
}
