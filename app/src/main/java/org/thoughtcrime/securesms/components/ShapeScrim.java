package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.thoughtcrime.securesms.R;

public class ShapeScrim extends View {

  private enum ShapeType {
    CIRCLE, SQUARE
  }

  private final Paint     eraser;
  private final ShapeType shape;
  private final float     radius;
  private final int       canvasColor;

  private Bitmap scrim;
  private Canvas scrimCanvas;
  private int    scrimWidth;
  private int    scrimHeight;

  public ShapeScrim(Context context) {
    this(context, null);
  }

  public ShapeScrim(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ShapeScrim(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ShapeScrim, 0, 0);
      String     shapeName  = typedArray.getString(R.styleable.ShapeScrim_shape);

      if      ("square".equalsIgnoreCase(shapeName)) this.shape = ShapeType.SQUARE;
      else if ("circle".equalsIgnoreCase(shapeName)) this.shape = ShapeType.CIRCLE;
      else                                           this.shape = ShapeType.SQUARE;

      this.radius = typedArray.getFloat(R.styleable.ShapeScrim_radius, 0.4f);

      typedArray.recycle();
    } else {
      this.shape  = ShapeType.SQUARE;
      this.radius = 0.4f;
    }

    this.eraser = new Paint();
    this.eraser.setColor(0xFFFFFFFF);
    this.eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

    this.canvasColor = Color.parseColor("#55BDBDBD");
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    int   shortDimension = Math.min(getWidth(), getHeight());
    float drawRadius     = shortDimension * radius;

    float left   = (getMeasuredWidth() / 2 ) - drawRadius;
    float top    = (getMeasuredHeight() / 2) - drawRadius;
    float right  = left + (drawRadius * 2);
    float bottom = top + (drawRadius * 2);

    scrimWidth  = (int) (right - left);
    scrimHeight = (int) (bottom - top);
  }

  @Override
  public void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int   shortDimension = Math.min(getWidth(), getHeight());
    float drawRadius     = shortDimension * radius;

    if (scrimCanvas == null) {
      scrim = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
      scrimCanvas = new Canvas(scrim);
    }

    scrim.eraseColor(Color.TRANSPARENT);
    scrimCanvas.drawColor(canvasColor);

    if (shape == ShapeType.CIRCLE) drawCircle(scrimCanvas, drawRadius, eraser);
    else                           drawSquare(scrimCanvas, drawRadius, eraser);

    canvas.drawBitmap(scrim, 0, 0, null);
  }

  @Override
  public void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldHeight, oldHeight);

    if (width != oldWidth || height != oldHeight) {
      scrim       = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      scrimCanvas = new Canvas(scrim);
    }
  }

  private void drawCircle(Canvas canvas, float radius, Paint eraser) {
    canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius, eraser);
  }

  private void drawSquare(Canvas canvas, float radius, Paint eraser) {
    float left   = (getWidth() / 2 ) - radius;
    float top    = (getHeight() / 2) - radius;
    float right  = left + (radius * 2);
    float bottom = top + (radius * 2);

    RectF square = new RectF(left, top, right, bottom);

    canvas.drawRoundRect(square, 25, 25, eraser);
  }

  public int getScrimWidth() {
    return scrimWidth;
  }

  public int getScrimHeight() {
    return scrimHeight;
  }
}
