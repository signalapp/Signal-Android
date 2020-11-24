package org.thoughtcrime.securesms.imageeditor;

import android.graphics.Matrix;
import android.graphics.RectF;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The local extent of a {@link org.thoughtcrime.securesms.imageeditor.model.EditorElement}.
 * i.e. all {@link org.thoughtcrime.securesms.imageeditor.model.EditorElement}s have a bounding rectangle from:
 * <p>
 * {@link #LEFT} to {@link #RIGHT} and from {@link #TOP} to {@link #BOTTOM}.
 */
public final class Bounds {

  public static final float LEFT   = -1000f;
  public static final float RIGHT  =  1000f;

  public static final float TOP    = -1000f;
  public static final float BOTTOM =  1000f;

  public static final float CENTRE_X =  (LEFT + RIGHT) / 2f;
  public static final float CENTRE_Y =  (TOP + BOTTOM) / 2f;

  public static final float[] CENTRE = new float[]{ CENTRE_X, CENTRE_Y };

  private static final float[] POINTS = { Bounds.LEFT,  Bounds.TOP,
                                          Bounds.RIGHT, Bounds.TOP,
                                          Bounds.RIGHT, Bounds.BOTTOM,
                                          Bounds.LEFT,  Bounds.BOTTOM };

  static RectF newFullBounds() {
    return new RectF(LEFT, TOP, RIGHT, BOTTOM);
  }

  public static RectF FULL_BOUNDS = newFullBounds();

  public static boolean contains(float x, float y) {
    return x >= FULL_BOUNDS.left && x <= FULL_BOUNDS.right &&
           y >= FULL_BOUNDS.top  && y <= FULL_BOUNDS.bottom;
  }

  /**
   * Maps all the points of bounds with the supplied matrix and determines whether they are still in bounds.
   *
   * @param matrix matrix to transform points by, null is treated as identity.
   * @return true iff all points remain in bounds after transformation.
   */
  public static boolean boundsRemainInBounds(@Nullable Matrix matrix) {
    if (matrix == null) return true;

    float[] dst = new float[POINTS.length];

    matrix.mapPoints(dst, POINTS);

    return allWithinBounds(dst);
  }

  private static boolean allWithinBounds(@NonNull float[] points) {
    boolean allHit = true;

    for (int i = 0; i < points.length / 2; i++) {
      float x = points[2 * i];
      float y = points[2 * i + 1];

      if (!Bounds.contains(x, y)) {
        allHit = false;
        break;
      }
    }

    return allHit;
  }
}
