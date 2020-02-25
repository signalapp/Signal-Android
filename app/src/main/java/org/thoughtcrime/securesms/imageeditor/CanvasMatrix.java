package org.thoughtcrime.securesms.imageeditor;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import androidx.annotation.NonNull;

/**
 * Tracks the current matrix for a canvas.
 * <p>
 * This is because you cannot reliably call {@link Canvas#setMatrix(Matrix)}.
 * {@link Canvas#getMatrix()} provides this hint in its documentation:
 * "track relevant transform state outside of the canvas."
 * <p>
 * To achieve this, any changes to the canvas matrix must be done via this class, including save and
 * restore operations where the matrix was altered in between.
 */
public final class CanvasMatrix {

  private final static int STACK_HEIGHT_LIMIT = 16;

  private final Canvas   canvas;
  private final Matrix   canvasMatrix   = new Matrix();
  private final Matrix   temp           = new Matrix();
  private final Matrix[] stack          = new Matrix[STACK_HEIGHT_LIMIT];
  private       int      stackHeight;

  CanvasMatrix(Canvas canvas) {
    this.canvas = canvas;
    for (int i = 0; i < stack.length; i++) {
      stack[i] = new Matrix();
    }
  }

  public void concat(@NonNull Matrix matrix) {
    canvas.concat(matrix);
    canvasMatrix.preConcat(matrix);
  }

  void save() {
    canvas.save();
    if (stackHeight == STACK_HEIGHT_LIMIT) {
      throw new AssertionError("Not enough space on stack");
    }
    stack[stackHeight++].set(canvasMatrix);
  }

  void restore() {
    canvas.restore();
    canvasMatrix.set(stack[--stackHeight]);
  }

  void getCurrent(@NonNull Matrix into) {
    into.set(canvasMatrix);
  }

  public void setToIdentity() {
    if (canvasMatrix.invert(temp)) {
      concat(temp);
    }
  }

  public void initial(Matrix viewMatrix) {
    concat(viewMatrix);
  }

  boolean mapRect(@NonNull RectF dst, @NonNull RectF src) {
    return canvasMatrix.mapRect(dst, src);
  }

  public void mapPoints(float[] dst, float[] src) {
    canvasMatrix.mapPoints(dst, src);
  }

  public void copyTo(@NonNull Matrix matrix) {
    matrix.set(canvasMatrix);
  }
}
