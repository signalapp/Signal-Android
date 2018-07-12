package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import org.thoughtcrime.securesms.R;

public class CornerMaskingView extends FrameLayout {

  private final float[] radii      = new float[8];
  private final Paint   dstPaint   = new Paint();
  private final Paint   clearPaint = new Paint();
  private final Path    outline    = new Path();
  private final Path    corners    = new Path();
  private final RectF   bounds     = new RectF();

  public CornerMaskingView(@NonNull Context context) {
    super(context);
    init(null);
  }

  public CornerMaskingView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public CornerMaskingView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    setLayerType(LAYER_TYPE_HARDWARE, null);

    dstPaint.setColor(Color.BLACK);
    dstPaint.setStyle(Paint.Style.FILL);
    dstPaint.setAntiAlias(true);
    dstPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

    clearPaint.setColor(Color.BLACK);
    clearPaint.setStyle(Paint.Style.FILL);
    clearPaint.setAntiAlias(true);
    clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.CornerMaskingView, 0, 0);
      setRadius(typedArray.getDimensionPixelOffset(R.styleable.CornerMaskingView_cmv_radius, 0));
      typedArray.recycle();
    }
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);

    bounds.left   = 0;
    bounds.top    = 0;
    bounds.right  = canvas.getWidth();
    bounds.bottom = canvas.getHeight();

    corners.reset();
    corners.addRoundRect(bounds, radii, Path.Direction.CW);

    // Note: There's a bug in the P beta where most PorterDuff modes aren't working. But CLEAR does.
    //       So we find and inverse path and use Mode.CLEAR for versions that support Path.op().
    //       See issue https://issuetracker.google.com/issues/111394085.
    if (Build.VERSION.SDK_INT >= 19) {
      outline.reset();
      outline.addRect(bounds, Path.Direction.CW);
      outline.op(corners, Path.Op.DIFFERENCE);
      canvas.drawPath(outline, clearPaint);
    } else {
      corners.addRoundRect(bounds, radii, Path.Direction.CW);
      canvas.drawPath(corners, dstPaint);
    }
  }

  public void setRadius(int radius) {
    setRadii(radius, radius, radius, radius);
  }

  public void setRadii(int topLeft, int topRight, int bottomRight, int bottomLeft) {
    radii[0] = radii[1] = topLeft;
    radii[2] = radii[3] = topRight;
    radii[4] = radii[5] = bottomRight;
    radii[6] = radii[7] = bottomLeft;
    invalidate();
  }

  public void setTopLeftRadius(int radius) {
    radii[0] = radii[1] = radius;
    invalidate();
  }

  public void setTopRightRadius(int radius) {
    radii[2] = radii[3] = radius;
    invalidate();
  }

  public void setBottomRightRadius(int radius) {
    radii[4] = radii[5] = radius;
    invalidate();
  }

  public void setBottomLeftRadius(int radius) {
    radii[6] = radii[7] = radius;
    invalidate();
  }
}
