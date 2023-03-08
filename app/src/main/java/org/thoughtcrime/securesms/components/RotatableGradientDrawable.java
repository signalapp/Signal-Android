package org.thoughtcrime.securesms.components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Xfermode;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

import kotlin.jvm.functions.Function2;

/**
 * Drawable which renders a gradient at a specified angle. Note that this drawable does
 * not implement drawable state, and all the baggage that comes with a normal Drawable
 * override, so this may not work in every scenario.
 *
 * Essentially, this drawable creates a LinearGradient shader using the given colors and
 * positions, but makes it larger than the bounds, such that it can be rotated and still
 * fill the bounds with a gradient.
 *
 * If you wish to apply clipping to this drawable, it is recommended to either use it with
 * a MaterialCardView or utilize {@link org.thoughtcrime.securesms.util.CustomDrawWrapperKt#customizeOnDraw(Drawable, Function2)}
 */
public final class RotatableGradientDrawable extends Drawable {

  /**
   * From investigation into how Gradients are rendered vs how they are rendered in
   * designs, in order to match spec, we need to rotate gradients by 225 degrees. (180 + 45)
   *
   * This puts 0 at the bottom (0, -1) of the surface area.
   */
  private static final float DEGREE_OFFSET = 225f;

  private final float   degrees;
  private final int[]   colors;
  private final float[] positions;

  private final Rect  fillRect  = new Rect();
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

  /**
   * @param degrees    Gradient rotation in degrees, relative to a vector pointed from the center to bottom center
   * @param colors     The colors of the gradient
   * @param positions  The positions of the colors. Values should be between 0f and 1f and this array should be the
   *                   same length as colors.
   */
  public RotatableGradientDrawable(float degrees, int[] colors, @Nullable float[] positions) {
    this.degrees   = degrees + DEGREE_OFFSET;
    this.colors    = colors;
    this.positions = positions;
  }

  @Override
  public void setBounds(int left, int top, int right, int bottom) {
    super.setBounds(left, top, right, bottom);

    Point topLeft     = new Point(left, top);
    Point topRight    = new Point(right, top);
    Point bottomLeft  = new Point(left, bottom);
    Point bottomRight = new Point(right, bottom);
    Point origin      = new Point(getBounds().width() / 2, getBounds().height() / 2);

    Point rotationTopLeft     = cornerPrime(origin, topLeft, degrees);
    Point rotationTopRight    = cornerPrime(origin, topRight, degrees);
    Point rotationBottomLeft  = cornerPrime(origin, bottomLeft, degrees);
    Point rotationBottomRight = cornerPrime(origin, bottomRight, degrees);

    fillRect.left   = Integer.MAX_VALUE;
    fillRect.top    = Integer.MAX_VALUE;
    fillRect.right  = Integer.MIN_VALUE;
    fillRect.bottom = Integer.MIN_VALUE;

    for (Point point : Arrays.asList(topLeft, topRight, bottomLeft, bottomRight, rotationTopLeft, rotationTopRight, rotationBottomLeft, rotationBottomRight)) {
      if (point.x < fillRect.left) {
        fillRect.left = point.x;
      }

      if (point.x > fillRect.right) {
        fillRect.right = point.x;
      }

      if (point.y < fillRect.top) {
        fillRect.top = point.y;
      }

      if (point.y > fillRect.bottom) {
        fillRect.bottom = point.y;
      }
    }

    fillPaint.setShader(new LinearGradient(fillRect.left, fillRect.top, fillRect.right, fillRect.bottom, colors, positions, Shader.TileMode.CLAMP));
  }

  public void setXfermode(@NonNull Xfermode xfermode) {
    fillPaint.setXfermode(xfermode);
  }

  private static Point cornerPrime(@NonNull Point origin, @NonNull Point corner, float degrees) {
    return new Point(xPrime(origin, corner, Math.toRadians(degrees)), yPrime(origin, corner, Math.toRadians(degrees)));
  }

  private static int xPrime(@NonNull Point origin, @NonNull Point corner, double theta) {
    return (int) Math.ceil(((corner.x - origin.x) * Math.cos(theta)) - ((corner.y - origin.y) * Math.sin(theta)) + origin.x);
  }

  private static int yPrime(@NonNull Point origin, @NonNull Point corner, double theta) {
    return (int) Math.ceil(((corner.x - origin.x) * Math.sin(theta)) + ((corner.y - origin.y) * Math.cos(theta)) + origin.y);
  }

  @Override
  public void draw(Canvas canvas) {
    int save = canvas.save();
    canvas.rotate(degrees, getBounds().width() / 2f, getBounds().height() / 2f);

    int height = fillRect.height();
    int width = fillRect.width();
    canvas.drawRect(fillRect.left - width, fillRect.top - height, fillRect.right + width, fillRect.bottom + height, fillPaint);

    canvas.restoreToCount(save);
  }

  @Override
  public void setAlpha(int alpha) {
    // Not supported
  }

  @Override
  public void setColorFilter(@Nullable ColorFilter colorFilter) {
    // Not supported
  }

  @Override
  public int getOpacity() {
    return PixelFormat.OPAQUE;
  }
}
