package org.thoughtcrime.securesms.components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

public class Outliner {

  private final float[] radii        = new float[8];
  private final Path    corners      = new Path();
  private final RectF   bounds       = new RectF();
  private final Paint   outlinePaint = new Paint();
  {
    outlinePaint.setStyle(Paint.Style.STROKE);
    outlinePaint.setStrokeWidth(1f);
    outlinePaint.setAntiAlias(true);
  }

  public void setColor(@ColorInt int color) {
    outlinePaint.setColor(color);
  }

  public void draw(Canvas canvas) {
    final float halfStrokeWidth = outlinePaint.getStrokeWidth() / 2;

    bounds.left   = halfStrokeWidth;
    bounds.top    = halfStrokeWidth;
    bounds.right  = canvas.getWidth() - halfStrokeWidth;
    bounds.bottom = canvas.getHeight() - halfStrokeWidth;

    corners.reset();
    corners.addRoundRect(bounds, radii, Path.Direction.CW);

    canvas.drawPath(corners, outlinePaint);
  }

  public void setRadius(int radius) {
    setRadii(radius, radius, radius, radius);
  }

  public void setRadii(int topLeft, int topRight, int bottomRight, int bottomLeft) {
    radii[0] = radii[1] = topLeft;
    radii[2] = radii[3] = topRight;
    radii[4] = radii[5] = bottomRight;
    radii[6] = radii[7] = bottomLeft;
  }
}
