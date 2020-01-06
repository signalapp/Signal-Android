package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;

public class ArcProgressBar extends View {

  private static final int     DEFAULT_WIDTH            = 10;
  private static final float   DEFAULT_PROGRESS         = 0f;
  private static final int     DEFAULT_BACKGROUND_COLOR = 0xFF000000;
  private static final int     DEFAULT_FOREGROUND_COLOR = 0xFFFFFFFF;
  private static final float   DEFAULT_START_ANGLE      = 0f;
  private static final float   DEFAULT_SWEEP_ANGLE      = 360f;
  private static final boolean DEFAULT_ROUNDED_ENDS     = true;

  private static final String SUPER    = "arcprogressbar.super";
  private static final String PROGRESS = "arcprogressbar.progress";

  private       float progress;
  private final float width;
  private final RectF arcRect = new RectF();

  private final Paint arcBackgroundPaint;
  private final Paint arcForegroundPaint;
  private final float arcStartAngle;
  private final float arcSweepAngle;

  public ArcProgressBar(@NonNull Context context) {
    this(context, null);
  }

  public ArcProgressBar(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ArcProgressBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ArcProgressBar, defStyleAttr, 0);

    width              = attributes.getDimensionPixelSize(R.styleable.ArcProgressBar_arcWidth, DEFAULT_WIDTH);
    progress           = attributes.getFloat(R.styleable.ArcProgressBar_arcProgress, DEFAULT_PROGRESS);
    arcBackgroundPaint = createPaint(width, attributes.getColor(R.styleable.ArcProgressBar_arcBackgroundColor, DEFAULT_BACKGROUND_COLOR));
    arcForegroundPaint = createPaint(width, attributes.getColor(R.styleable.ArcProgressBar_arcForegroundColor, DEFAULT_FOREGROUND_COLOR));
    arcStartAngle      = attributes.getFloat(R.styleable.ArcProgressBar_arcStartAngle, DEFAULT_START_ANGLE);
    arcSweepAngle      = attributes.getFloat(R.styleable.ArcProgressBar_arcSweepAngle, DEFAULT_SWEEP_ANGLE);

    if (attributes.getBoolean(R.styleable.ArcProgressBar_arcRoundedEnds, DEFAULT_ROUNDED_ENDS)) {
      arcForegroundPaint.setStrokeCap(Paint.Cap.ROUND);

      if (arcSweepAngle <= 360f) {
        arcBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
      }
    }

    attributes.recycle();
  }

  private static Paint createPaint(float width, @ColorInt int color) {
    Paint paint = new Paint();

    paint.setStrokeWidth(width);
    paint.setStyle(Paint.Style.STROKE);
    paint.setAntiAlias(true);
    paint.setColor(color);

    return paint;
  }

  public void setProgress(float progress) {
    if (this.progress != progress) {
      this.progress = progress;
      invalidate();
    }
  }

  @Override
  protected @Nullable Parcelable onSaveInstanceState() {
    Parcelable superState = super.onSaveInstanceState();

    Bundle bundle = new Bundle();
    bundle.putParcelable(SUPER, superState);
    bundle.putFloat(PROGRESS, progress);

    return bundle;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    if (state.getClass() != Bundle.class) throw new IllegalStateException("Expected");

    Bundle restoreState = (Bundle) state;

    Parcelable superState = restoreState.getParcelable(SUPER);
    super.onRestoreInstanceState(superState);

    progress = restoreState.getLong(PROGRESS);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float halfWidth = width / 2f;
    arcRect.set(0           + halfWidth,
                0           + halfWidth,
                getWidth()  - halfWidth,
                getHeight() - halfWidth);

    canvas.drawArc(arcRect, arcStartAngle, arcSweepAngle, false, arcBackgroundPaint);
    canvas.drawArc(arcRect, arcStartAngle, arcSweepAngle * Util.clamp(progress, 0f, 1f), false, arcForegroundPaint);
  }
}
