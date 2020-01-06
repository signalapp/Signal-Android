package org.thoughtcrime.securesms.imageeditor;

import android.graphics.Matrix;
import android.graphics.PointF;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.imageeditor.model.EditorElement;

abstract class ElementEditSession implements EditSession {

  private final Matrix inverseMatrix;

  final EditorElement selected;

  final PointF[] startPointElement = newTwoPointArray();
  final PointF[] endPointElement   = newTwoPointArray();
  final PointF[] startPointScreen  = newTwoPointArray();
  final PointF[] endPointScreen    = newTwoPointArray();

  ElementEditSession(@NonNull EditorElement selected, @NonNull Matrix inverseMatrix) {
    this.selected = selected;
    this.inverseMatrix = inverseMatrix;
  }

  void setScreenStartPoint(int p, @NonNull PointF point) {
    startPointScreen[p] = point;
    mapPoint(startPointElement[p], inverseMatrix, point);
  }

  void setScreenEndPoint(int p, @NonNull PointF point) {
    endPointScreen[p] = point;
    mapPoint(endPointElement[p], inverseMatrix, point);
  }

  @Override
  public abstract void movePoint(int p, @NonNull PointF point);

  @Override
  public void commit() {
    selected.commitEditorMatrix();
  }

  @Override
  public EditorElement getSelected() {
    return selected;
  }

  private static PointF[] newTwoPointArray() {
    PointF[] array = new PointF[2];
    for (int i = 0; i < array.length; i++) {
      array[i] = new PointF();
    }
    return array;
  }

  /**
   * Map src to dst using the matrix.
   *
   * @param dst    Output point.
   * @param matrix Matrix to transform point with.
   * @param src    Input point.
   */
  private static void mapPoint(@NonNull PointF dst, @NonNull Matrix matrix, @NonNull PointF src) {
    float[] in = { src.x, src.y };
    float[] out = new float[2];
    matrix.mapPoints(out, in);
    dst.set(out[0], out[1]);
  }
}
