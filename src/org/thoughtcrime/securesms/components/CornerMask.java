package org.thoughtcrime.securesms.components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import android.view.View;

public class CornerMask {

  private final float[] radii      = new float[8];
  private final Paint   clearPaint = new Paint();
  private final Path    outline    = new Path();
  private final Path    corners    = new Path();
  private final RectF   bounds     = new RectF();

  public CornerMask(@NonNull View view) {
    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

    clearPaint.setColor(Color.BLACK);
    clearPaint.setStyle(Paint.Style.FILL);
    clearPaint.setAntiAlias(true);
    clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
  }

  public void mask(Canvas canvas) {
    bounds.left   = 0;
    bounds.top    = 0;
    bounds.right  = canvas.getWidth();
    bounds.bottom = canvas.getHeight();

    corners.reset();
    corners.addRoundRect(bounds, radii, Path.Direction.CW);

    // Note: There's a bug in the P beta where most PorterDuff modes aren't working. But CLEAR does.
    //       So we find and inverse path and use Mode.CLEAR.
    //       See issue https://issuetracker.google.com/issues/111394085.
    outline.reset();
    outline.addRect(bounds, Path.Direction.CW);
    outline.op(corners, Path.Op.DIFFERENCE);
    canvas.drawPath(outline, clearPaint);
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

  public void setTopLeftRadius(int radius) {
    radii[0] = radii[1] = radius;
  }

  public void setTopRightRadius(int radius) {
    radii[2] = radii[3] = radius;
  }

  public void setBottomRightRadius(int radius) {
    radii[4] = radii[5] = radius;
  }

  public void setBottomLeftRadius(int radius) {
    radii[6] = radii[7] = radius;
  }
}
