package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;

import org.thoughtcrime.securesms.R;

public class HourglassView extends View {

  private final Paint foregroundPaint;
  private final Paint backgroundPaint;
  private final Paint progressPaint;

  private Bitmap empty;
  private Bitmap full;
  private int    tint;

  private float percentage;
  private int   offset;

  public HourglassView(Context context) {
    this(context, null);
  }

  public HourglassView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public HourglassView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.HourglassView, 0, 0);
      this.empty = BitmapFactory.decodeResource(getResources(), typedArray.getResourceId(R.styleable.HourglassView_empty, 0));
      this.full  = BitmapFactory.decodeResource(getResources(), typedArray.getResourceId(R.styleable.HourglassView_full, 0));
      this.tint  = typedArray.getColor(R.styleable.HourglassView_tint, 0);
      this.percentage = typedArray.getInt(R.styleable.HourglassView_percentage, 50);
      this.offset = typedArray.getInt(R.styleable.HourglassView_offset, 0);
      typedArray.recycle();
    }

    this.backgroundPaint = new Paint();
    this.foregroundPaint = new Paint();
    this.progressPaint   = new Paint();

    setPaintBasedOnTint();

    this.progressPaint.setColor(getResources().getColor(R.color.black));
    this.progressPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

    if (android.os.Build.VERSION.SDK_INT >= 11)
    {
      setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }
  }

  private void setPaintBasedOnTint() {
    this.backgroundPaint.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.MULTIPLY));
    this.foregroundPaint.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.MULTIPLY));
  }

  @Override
  public void onDraw(Canvas canvas) {
    float progressHeight = (full.getHeight() - (offset*2)) * (percentage / 100);

    canvas.drawBitmap(full, 0, 0, backgroundPaint);
    canvas.drawRect(0, 0, full.getWidth(), offset + progressHeight, progressPaint);
    canvas.drawBitmap(empty, 0, 0, foregroundPaint);
  }

  public void setPercentage(float percentage) {
    this.percentage = percentage;
    invalidate();
  }

  public void setTint(int tint) {
    this.tint = tint;

    setPaintBasedOnTint();
  }
}
