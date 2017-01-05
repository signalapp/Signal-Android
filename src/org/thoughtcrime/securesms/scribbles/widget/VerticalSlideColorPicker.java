/**
 * Copyright (c) 2016 Mark Charles
 * Copyright (c) 2016 Open Whisper Systems
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.thoughtcrime.securesms.scribbles.widget;


import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.thoughtcrime.securesms.R;

public class VerticalSlideColorPicker extends View {

  private Paint  paint;
  private Paint  strokePaint;
  private Path   path;
  private Bitmap bitmap;
  private Canvas bitmapCanvas;

  private int     viewWidth;
  private int     viewHeight;
  private int     centerX;
  private float   colorPickerRadius;
  private RectF   colorPickerBody;

  private OnColorChangeListener onColorChangeListener;

  private int     borderColor;
  private float   borderWidth;
  private int[]   colors;

  public VerticalSlideColorPicker(Context context) {
    super(context);
    init();
  }

  public VerticalSlideColorPicker(Context context, AttributeSet attrs) {
    super(context, attrs);

    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.VerticalSlideColorPicker, 0, 0);

    try {
      int colorsResourceId = a.getResourceId(R.styleable.VerticalSlideColorPicker_pickerColors, R.array.scribble_colors);

      colors      = a.getResources().getIntArray(colorsResourceId);
      borderColor = a.getColor(R.styleable.VerticalSlideColorPicker_pickerBorderColor, Color.WHITE);
      borderWidth = a.getDimension(R.styleable.VerticalSlideColorPicker_pickerBorderWidth, 10f);

    } finally {
      a.recycle();
    }

    init();
  }

  public VerticalSlideColorPicker(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public VerticalSlideColorPicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init();
  }

  private void init() {
    setWillNotDraw(false);

    paint = new Paint();
    paint.setStyle(Paint.Style.FILL);
    paint.setAntiAlias(true);

    path = new Path();

    strokePaint = new Paint();
    strokePaint.setStyle(Paint.Style.STROKE);
    strokePaint.setColor(borderColor);
    strokePaint.setAntiAlias(true);
    strokePaint.setStrokeWidth(borderWidth);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    path.addCircle(centerX, borderWidth + colorPickerRadius, colorPickerRadius, Path.Direction.CW);
    path.addRect(colorPickerBody, Path.Direction.CW);
    path.addCircle(centerX, viewHeight - (borderWidth + colorPickerRadius), colorPickerRadius, Path.Direction.CW);

    bitmapCanvas.drawColor(Color.TRANSPARENT);

    bitmapCanvas.drawPath(path, strokePaint);
    bitmapCanvas.drawPath(path, paint);

    canvas.drawBitmap(bitmap, 0, 0, null);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {

    float yPos = Math.min(event.getY(), colorPickerBody.bottom);
    yPos = Math.max(colorPickerBody.top, yPos);

    int selectedColor = bitmap.getPixel(viewWidth/2, (int) yPos);

    if (onColorChangeListener != null) {
      onColorChangeListener.onColorChange(selectedColor);
    }

    return true;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);

    viewWidth = w;
    viewHeight = h;

    centerX           = viewWidth / 2;
    colorPickerRadius = (viewWidth / 2) - borderWidth;

    colorPickerBody = new RectF(centerX - colorPickerRadius, borderWidth + colorPickerRadius, centerX + colorPickerRadius, viewHeight - (borderWidth + colorPickerRadius));

    LinearGradient gradient = new LinearGradient(0, colorPickerBody.top, 0, colorPickerBody.bottom, colors, null, Shader.TileMode.CLAMP);
    paint.setShader(gradient);

    if (bitmap != null) {
      bitmap.recycle();
    }

    bitmap       = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
    bitmapCanvas = new Canvas(bitmap);

    resetToDefault();
  }

  public void setBorderColor(int borderColor) {
    this.borderColor = borderColor;
    invalidate();
  }

  public void setBorderWidth(float borderWidth) {
    this.borderWidth = borderWidth;
    invalidate();
  }

  public void setColors(int[] colors) {
    this.colors = colors;
    invalidate();
  }

  public void resetToDefault() {
    if (onColorChangeListener != null) {
      onColorChangeListener.onColorChange(Color.RED);
    }

    invalidate();
  }

  public void setOnColorChangeListener(OnColorChangeListener onColorChangeListener) {
    this.onColorChangeListener = onColorChangeListener;
  }

  public interface OnColorChangeListener {

    void onColorChange(int selectedColor);
  }
}