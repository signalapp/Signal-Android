package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.appcompat.widget.AppCompatSeekBar;

import org.thoughtcrime.securesms.R;

import java.util.Arrays;

public final class WaveFormSeekBarView extends AppCompatSeekBar {

  private static final int ANIM_DURATION             = 450;
  private static final int ANIM_BAR_OFF_SET_DURATION =  12;

  private final Interpolator overshoot        = new OvershootInterpolator();
  private final Paint        paint            = new Paint();
  private       float[]      data             = new float[0];
  private       long         dataSetTime;
  private       Drawable     progressDrawable;
  private       boolean      waveMode;
  
  @ColorInt private int playedBarColor   = 0xffffffff;
  @ColorInt private int unplayedBarColor = 0x7fffffff;
  @Px       private int barWidth;

  public WaveFormSeekBarView(Context context) {
    super(context);
    init();
  }

  public WaveFormSeekBarView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public WaveFormSeekBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setWillNotDraw(false);
    
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setAntiAlias(true);

    progressDrawable = super.getProgressDrawable();

    if (isInEditMode()) {
      setWaveData(sinusoidalExampleData());
      dataSetTime = 0;
    }

    barWidth = getResources().getDimensionPixelSize(R.dimen.wave_form_bar_width);
  }

  public void setColors(@ColorInt int playedBarColor, @ColorInt int unplayedBarColor, @ColorInt int thumbTint) {
    this.playedBarColor   = playedBarColor;
    this.unplayedBarColor = unplayedBarColor;

    getThumb().setColorFilter(thumbTint, PorterDuff.Mode.SRC_IN);

    invalidate();
  }

  @Override
  public void setProgressDrawable(Drawable progressDrawable) {
    this.progressDrawable = progressDrawable;
    if (!waveMode) {
      super.setProgressDrawable(progressDrawable);
    }
  }

  @Override
  public Drawable getProgressDrawable() {
    return progressDrawable;
  }

  public void setWaveData(@NonNull float[] data) {
    if (!Arrays.equals(data, this.data)) {
      this.data        = data;
      this.dataSetTime = System.currentTimeMillis();
    }
    setWaveMode(data.length > 0);
  }

  public void setWaveMode(boolean waveMode) {
    this.waveMode = waveMode;
    super.setProgressDrawable(this.waveMode ? null : progressDrawable);
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (waveMode) {
      drawWave(canvas);
    }
    super.onDraw(canvas);
  }

  private void drawWave(Canvas canvas) {
    paint.setStrokeWidth(barWidth);

    int   usableHeight = getHeight() - getPaddingTop() - getPaddingBottom();
    int   usableWidth  = getWidth() - getPaddingLeft() - getPaddingRight();
    float midpoint     = usableHeight / 2f;
    float maxHeight    = usableHeight / 2f - barWidth;
    float barGap       = (usableWidth - data.length * barWidth) / (float) (data.length - 1);

    boolean hasMoreFrames = false;

    canvas.save();
    canvas.translate(getPaddingLeft(), getPaddingTop());

    if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
      canvas.scale(-1, 1, usableWidth / 2f, usableHeight / 2f);
    }

    for (int bar = 0; bar < data.length; bar++) {
      float x        = bar * (barWidth + barGap) + barWidth / 2f;
      float y        = data[bar] * maxHeight;
      float progress = x / usableWidth;

      paint.setColor(progress * getMax() < getProgress() ? playedBarColor : unplayedBarColor);

      long  time             = System.currentTimeMillis() - bar * ANIM_BAR_OFF_SET_DURATION - dataSetTime;
      float timeX            = Math.max(0, Math.min(1, time / (float) ANIM_DURATION));
      float interpolatedTime = overshoot.getInterpolation(timeX);
      float interpolatedY    = y * interpolatedTime;

      canvas.drawLine(x, midpoint - interpolatedY, x, midpoint + interpolatedY, paint);

      if (time < ANIM_DURATION) {
        hasMoreFrames = true;
      }
    }

    canvas.restore();

    if (hasMoreFrames) {
      invalidate();
    }
  }

  private static float[] sinusoidalExampleData() {
    float[] data = new float[21];
    for (int i = 0; i < data.length; i++) {
      data[i] = (float) Math.sin(i / (float) (data.length - 1) * 2 * Math.PI);
    }
    return data;
  }
}
