package org.thoughtcrime.securesms.imageeditor;

import android.graphics.RectF;

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

  static RectF newFullBounds() {
    return new RectF(LEFT, TOP, RIGHT, BOTTOM);
  }

  public static RectF FULL_BOUNDS = newFullBounds();
}
