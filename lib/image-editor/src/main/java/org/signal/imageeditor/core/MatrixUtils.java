package org.signal.imageeditor.core;

import android.graphics.Matrix;

import androidx.annotation.NonNull;

public final class MatrixUtils {

  private static final ThreadLocal<float[]> tempMatrixValues = new ThreadLocal<>();

  protected static @NonNull float[] getTempMatrixValues() {
    float[] floats = tempMatrixValues.get();
    if(floats == null) {
      floats = new float[9];
      tempMatrixValues.set(floats);
    }
    return floats;
  }

  /**
   * Extracts the angle from a matrix in radians.
   */
  public static float getRotationAngle(@NonNull Matrix matrix) {
    float[] matrixValues = getTempMatrixValues();
    matrix.getValues(matrixValues);
    return (float) -Math.atan2(matrixValues[Matrix.MSKEW_X], matrixValues[Matrix.MSCALE_X]);
  }

  /** Gets the scale on the X axis */
  public static float getScaleX(@NonNull Matrix matrix) {
    float[] matrixValues = getTempMatrixValues();
    matrix.getValues(matrixValues);
    float scaleX = matrixValues[Matrix.MSCALE_X];
    float skewX = matrixValues[Matrix.MSKEW_X];
    return (float) Math.sqrt(scaleX * scaleX + skewX * skewX);
  }
}
