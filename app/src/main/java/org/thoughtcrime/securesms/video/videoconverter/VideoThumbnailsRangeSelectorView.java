package org.thoughtcrime.securesms.video.videoconverter;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RequiresApi;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.MemoryUnitFormat;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = 23)
public final class VideoThumbnailsRangeSelectorView extends VideoThumbnailsView {

  private static final String TAG = Log.tag(VideoThumbnailsRangeSelectorView.class);

  private static final long MINIMUM_SELECTABLE_RANGE = TimeUnit.MILLISECONDS.toMicros(500);
  private static final int  ANIMATION_DURATION_MS    = 100;

  private final Paint    paint                    = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint    paintGrey                = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint    thumbTimeTextPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint    thumbTimeBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect     tempDrawRect             = new Rect();
  private final RectF    timePillRect             = new RectF();
  private       Drawable chevronLeft;
  private       Drawable chevronRight;

  @Px       private int                   left;
  @Px       private int                   right;
  @Px       private int                   cursor;
            private Long                  minValue;
            private Long                  maxValue;
            private Long                  externalMinValue;
            private Long                  externalMaxValue;
            private float                 xDown;
            private long                  downCursor;
            private long                  downMin;
            private long                  downMax;
            private Thumb                 dragThumb;
            private Thumb                 lastDragThumb;
            private OnRangeChangeListener onRangeChangeListener;
  @Px       private int                   thumbSizePixels;
  @Px       private int                   thumbTouchRadius;
  @Px       private int                   cursorPixels;
  @ColorInt private int                   cursorColor;
  @ColorInt private int                   thumbColor;
  @ColorInt private int                   thumbColorEdited;
            private long                  actualPosition;
            private long                  dragPosition;
  @Px       private int                   thumbHintTextSize;
  @ColorInt private int                   thumbHintTextColor;
  @ColorInt private int                   thumbHintBackgroundColor;
            private long                  dragStartTimeMs;
            private long                  dragEndTimeMs;
            private long                  maximumSelectableRangeMicros;
            private Quality               outputQuality;
            private long                  qualityAvailableTimeMs;

  public VideoThumbnailsRangeSelectorView(final Context context) {
    super(context);
    init(null);
  }

  public VideoThumbnailsRangeSelectorView(final Context context, final @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public VideoThumbnailsRangeSelectorView(final Context context, final @Nullable AttributeSet attrs, final int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(attrs);
  }

  private void init(final @Nullable AttributeSet attrs) {
    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.VideoThumbnailsRangeSelectorView, 0, 0);
      try {
        thumbSizePixels          = typedArray.getDimensionPixelSize(R.styleable.VideoThumbnailsRangeSelectorView_thumbWidth, 1);
        cursorPixels             = typedArray.getDimensionPixelSize(R.styleable.VideoThumbnailsRangeSelectorView_cursorWidth, 1);
        thumbColor               = typedArray.getColor(R.styleable.VideoThumbnailsRangeSelectorView_thumbColor, 0xffff0000);
        thumbColorEdited         = typedArray.getColor(R.styleable.VideoThumbnailsRangeSelectorView_thumbColorEdited, thumbColor);
        cursorColor              = typedArray.getColor(R.styleable.VideoThumbnailsRangeSelectorView_cursorColor, thumbColor);
        thumbTouchRadius         = typedArray.getDimensionPixelSize(R.styleable.VideoThumbnailsRangeSelectorView_thumbTouchRadius, 50);
        thumbHintTextSize        = typedArray.getDimensionPixelSize(R.styleable.VideoThumbnailsRangeSelectorView_thumbHintTextSize, 0);
        thumbHintTextColor       = typedArray.getColor(R.styleable.VideoThumbnailsRangeSelectorView_thumbHintTextColor, 0xffff0000);
        thumbHintBackgroundColor = typedArray.getColor(R.styleable.VideoThumbnailsRangeSelectorView_thumbHintBackgroundColor, 0xff00ff00);
      } finally {
        typedArray.recycle();
      }
    }

    chevronLeft  = VectorDrawableCompat.create(getResources(), R.drawable.ic_chevron_left_black_8dp, null);
    chevronRight = VectorDrawableCompat.create(getResources(), R.drawable.ic_chevron_right_black_8dp, null);

    paintGrey.setColor(0x7f000000);
    paintGrey.setStyle(Paint.Style.FILL_AND_STROKE);
    paintGrey.setStrokeWidth(1);

    paint.setStrokeWidth(2);

    thumbTimeTextPaint.setTextSize(thumbHintTextSize);
    thumbTimeTextPaint.setColor(thumbHintTextColor);

    thumbTimeBackgroundPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    thumbTimeBackgroundPaint.setColor(thumbHintBackgroundColor);
  }

  @Override
  protected void afterDurationChange(long duration) {
    super.afterDurationChange(duration);

    if (maxValue != null && duration < maxValue) {
      maxValue = duration;
    }

    if (minValue != null && duration < minValue) {
      minValue = duration;
    }

    if (duration > 0) {
      if (externalMinValue != null) {
        setMinMax(externalMinValue, getMaxValue(), Thumb.MIN);
        externalMinValue = null;
      }

      if (externalMaxValue != null) {
        setMinMax(getMinValue(), externalMaxValue, Thumb.MAX);
        externalMaxValue = null;
      }
    }

    if (setMinValue(getMinValue())) {
      Log.d(TAG, "Clamped video duration to " + getMaxValue());
      if (onRangeChangeListener != null) {
        onRangeChangeListener.onRangeDragEnd(getMinValue(), getMaxValue(), getDuration(), Thumb.MAX);
      }
    }

    if (onRangeChangeListener != null) {
      onRangeChangeListener.onRangeDragEnd(getMinValue(), getMaxValue(), getDuration(), Thumb.MIN);
      setOutputQuality(onRangeChangeListener.getQuality(getClipDuration(), getDuration()));
    }

    invalidate();
  }

  public void setOnRangeChangeListener(OnRangeChangeListener onRangeChangeListener) {
    this.onRangeChangeListener = onRangeChangeListener;
  }

  public void setActualPosition(long position) {
    if (this.actualPosition != position) {
      this.actualPosition = position;
      invalidate();
    }
  }

  private void setDragPosition(long position) {
    if (this.dragPosition != position) {
      this.dragPosition = Math.max(getMinValue(), Math.min(getMaxValue(), position));
      invalidate();
    }
  }

  @Override
  protected void onDraw(final Canvas canvas) {
    super.onDraw(canvas);

    canvas.translate(getPaddingLeft(), getPaddingTop());
    int drawableWidth  = getDrawableWidth();
    int drawableHeight = getDrawableHeight();

    long duration = getDuration();

    long min = getMinValue();
    long max = getMaxValue();

    boolean edited = min != 0 || max != duration;

    long drawPosAt = dragThumb == Thumb.POSITION ? dragPosition : actualPosition;

    left   = duration != 0 ? (int) ((min * drawableWidth) / duration) : 0;
    right  = duration != 0 ? (int) ((max * drawableWidth) / duration) : drawableWidth;
    cursor = duration != 0 ? (int) ((drawPosAt * drawableWidth) / duration) : drawableWidth;

    // draw greyed out areas
    tempDrawRect.set(0, 0, left - 1, drawableHeight);
    canvas.drawRect(tempDrawRect, paintGrey);
    tempDrawRect.set(right + 1, 0, drawableWidth, drawableHeight);
    canvas.drawRect(tempDrawRect, paintGrey);

    // draw area rectangle
    paint.setStyle(Paint.Style.STROKE);
    tempDrawRect.set(left, 0, right, drawableHeight);
    paint.setColor(edited ? thumbColorEdited : thumbColor);
    canvas.drawRect(tempDrawRect, paint);

    // draw thumb rectangles
    paint.setStyle(Paint.Style.FILL_AND_STROKE);
    tempDrawRect.set(left, 0, left + thumbSizePixels, drawableHeight);
    canvas.drawRect(tempDrawRect, paint);
    tempDrawRect.set(right - thumbSizePixels, 0, right, drawableHeight);
    canvas.drawRect(tempDrawRect, paint);

    int arrowSize = Math.min(drawableHeight, thumbSizePixels * 2);
    chevronLeft .setBounds(0, 0, arrowSize, arrowSize);
    chevronRight.setBounds(0, 0, arrowSize, arrowSize);

    float dy            = (drawableHeight  - arrowSize) / 2f;
    float arrowPaddingX = (thumbSizePixels - arrowSize) / 2f;

    // draw left thumb chevron
    canvas.save();
    canvas.translate(left + arrowPaddingX, dy);
    chevronLeft.draw(canvas);
    canvas.restore();

    // draw right thumb chevron
    canvas.save();
    canvas.translate(right - thumbSizePixels + arrowPaddingX, dy);
    chevronRight.draw(canvas);
    canvas.restore();

    // draw time hint pill
    if (thumbHintTextSize > 0) {
      if (dragStartTimeMs > 0 && (dragThumb == Thumb.MIN || dragThumb == Thumb.MAX)) {
        drawTimeHint(canvas, drawableWidth, drawableHeight, dragThumb, false);
      }
      if (dragEndTimeMs > 0 && (lastDragThumb == Thumb.MIN || lastDragThumb == Thumb.MAX)) {
        drawTimeHint(canvas, drawableWidth, drawableHeight, lastDragThumb, true);
      }

      canvas.save();
      canvas.translate(0, drawableHeight * 2);
      drawDurationAndSizeHint(canvas, drawableWidth);
      canvas.restore();
    }

    // draw current position marker
    if (left <= cursor && cursor <= right && dragThumb != Thumb.MIN && dragThumb != Thumb.MAX) {
      canvas.translate(cursorPixels / 2, 0);
      tempDrawRect.set(cursor, 0, cursor + cursorPixels, drawableHeight);
      paint.setColor(cursorColor);
      canvas.drawRect(tempDrawRect, paint);
    }
  }

  private void drawTimeHint(Canvas canvas, int drawableWidth, int drawableHeight, Thumb dragThumb, boolean fadeOut) {
    canvas.save();
    long   microsecondValue = dragThumb == Thumb.MIN ? getMinValue() : getMaxValue();
    long   seconds          = TimeUnit.MICROSECONDS.toSeconds(microsecondValue);
    String timeString       = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    float  topBottomPadding = thumbHintTextSize * 0.5f;
    float  leftRightPadding = thumbHintTextSize * 0.75f;

    thumbTimeTextPaint.getTextBounds(timeString, 0, timeString.length(), tempDrawRect);

    timePillRect.set(tempDrawRect.left - leftRightPadding, tempDrawRect.top - topBottomPadding, tempDrawRect.right + leftRightPadding, tempDrawRect.bottom + topBottomPadding);

    float halfPillWidth  = timePillRect.width()  / 2f;
    float halfPillHeight = timePillRect.height() / 2f;

    long  animationTime     = fadeOut ? ANIMATION_DURATION_MS - Math.min(ANIMATION_DURATION_MS, System.currentTimeMillis() - dragEndTimeMs)
                                      : Math.min(ANIMATION_DURATION_MS, System.currentTimeMillis() - dragStartTimeMs);
    float animationPosition = animationTime / (float) ANIMATION_DURATION_MS;
    float scaleIn           = 0.2f * animationPosition + 0.8f;
    int   alpha             = (int) (255 * animationPosition);

    if (dragThumb == Thumb.MAX) {
      canvas.translate(Math.min(right, drawableWidth - halfPillWidth), 0);
    } else {
      canvas.translate(Math.max(left, halfPillWidth), 0);
    }
    canvas.translate(0, drawableHeight + halfPillHeight);
    canvas.scale(scaleIn, scaleIn);
    thumbTimeBackgroundPaint.setAlpha(Math.round(alpha * 0.6f));
    thumbTimeTextPaint.setAlpha(alpha);
    canvas.translate(leftRightPadding - halfPillWidth, halfPillHeight);
    canvas.drawRoundRect(timePillRect, halfPillHeight, halfPillHeight, thumbTimeBackgroundPaint);
    canvas.drawText(timeString, 0, 0, thumbTimeTextPaint);
    canvas.restore();

    if (fadeOut && animationTime > 0 || !fadeOut && animationTime < ANIMATION_DURATION_MS) {
      invalidate();
    } else {
      if (fadeOut) {
        lastDragThumb = null;
      }
    }
  }

  private void drawDurationAndSizeHint(Canvas canvas, int drawableWidth) {
    if (outputQuality == null) return;

    canvas.save();
    long   microsecondValue = getMaxValue() - getMinValue();
    long   seconds          = TimeUnit.MICROSECONDS.toSeconds(microsecondValue);
    String durationAndSize  = String.format(Locale.getDefault(), "%d:%02d • %s", seconds / 60, seconds % 60, MemoryUnitFormat.formatBytes(outputQuality.fileSize, MemoryUnitFormat.MEGA_BYTES, true));
    float  topBottomPadding = thumbHintTextSize * 0.5f;
    float  leftRightPadding = thumbHintTextSize * 0.75f;

    thumbTimeTextPaint.getTextBounds(durationAndSize, 0, durationAndSize.length(), tempDrawRect);

    timePillRect.set(tempDrawRect.left - leftRightPadding, tempDrawRect.top - topBottomPadding, tempDrawRect.right + leftRightPadding, tempDrawRect.bottom + topBottomPadding);

    float halfPillWidth  = timePillRect.width()  / 2f;
    float halfPillHeight = timePillRect.height() / 2f;

    long  animationTime     = Math.min(ANIMATION_DURATION_MS, System.currentTimeMillis() - qualityAvailableTimeMs);
    float animationPosition = animationTime / (float) ANIMATION_DURATION_MS;
    float scaleIn           = 0.2f * animationPosition + 0.8f;
    int   alpha             = (int) (255 * animationPosition);

    canvas.translate(Math.max(halfPillWidth, Math.min((right + left) / 2f, drawableWidth - halfPillWidth)), - 2 * halfPillHeight);
    canvas.scale(scaleIn, scaleIn);
    thumbTimeBackgroundPaint.setAlpha(Math.round(alpha * 0.6f));
    thumbTimeTextPaint.setAlpha(alpha);
    canvas.translate(leftRightPadding - halfPillWidth, halfPillHeight);
    canvas.drawRoundRect(timePillRect, halfPillHeight, halfPillHeight, thumbTimeBackgroundPaint);
    canvas.drawText(durationAndSize, 0, 0, thumbTimeTextPaint);
    canvas.restore();

    if (animationTime < ANIMATION_DURATION_MS) {
      invalidate();
    }
  }

  public long getMinValue() {
    return minValue == null ? 0 : minValue;
  }

  public long getMaxValue() {
    return maxValue == null ? getDuration() : maxValue;
  }

  public long getClipDuration() {
    return getMaxValue() - getMinValue();
  }

  private boolean setMinValue(long minValue) {
    if (this.minValue == null || this.minValue != minValue) {
      return setMinMax(minValue, getMaxValue(), Thumb.MIN);
    } else{
      return false;
    }
  }

  public boolean setMaxValue(long maxValue) {
    if (this.maxValue == null || this.maxValue != maxValue) {
      return setMinMax(getMinValue(), maxValue, Thumb.MAX);
    } else{
      return false;
    }
  }

  private boolean setMinMax(long newMin, long newMax, Thumb thumb) {
    final long currentMin = getMinValue();
    final long currentMax = getMaxValue();
    final long duration   = getDuration();

    final long minDiff = Math.max(MINIMUM_SELECTABLE_RANGE, pixelToDuration(thumbSizePixels * 2.5f));
    final long maxDiff = maximumSelectableRangeMicros <= MINIMUM_SELECTABLE_RANGE ? 0 : Math.max(maximumSelectableRangeMicros, pixelToDuration(thumbSizePixels * 2.5f));

    if (thumb == Thumb.MIN) {
      newMin = clamp(newMin, 0, currentMax - minDiff);
      if (maxDiff > 0) {
        newMax = clamp(newMax, newMin + minDiff, Math.min(newMin + maxDiff, duration));
      }
    } else {
      newMax = clamp(newMax, currentMin + minDiff, duration);
      if (maxDiff > 0) {
        newMin = clamp(newMin, Math.max(0, newMax - maxDiff), newMax - minDiff);
      }
    }

    if (newMin != currentMin || newMax != currentMax) {
      this.minValue = newMin;
      this.maxValue = newMax;
      invalidate();
      return true;
    }
    return false;
  }

  private static long clamp(long value, long min, long max) {
    return Math.min(Math.max(min, value), max);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int actionMasked = event.getActionMasked();
    if (actionMasked == MotionEvent.ACTION_DOWN) {
      xDown           = event.getX();
      downCursor      = actualPosition;
      downMin         = getMinValue();
      downMax         = getMaxValue();
      dragThumb       = closestThumb(event.getX());
      dragStartTimeMs = System.currentTimeMillis();
      invalidate();
      return dragThumb != null;
    }

    if (actionMasked == MotionEvent.ACTION_MOVE) {
      boolean changed = false;
      long delta = pixelToDuration(event.getX() - xDown);
      switch (dragThumb) {
        case POSITION:
          setDragPosition(downCursor + delta);
          changed = true;
          break;
        case MIN:
          changed = setMinValue(downMin + delta);
          break;
        case MAX:
          changed = setMaxValue(downMax + delta);
          break;
      }
      if (changed && onRangeChangeListener != null) {
        if (dragThumb == Thumb.POSITION) {
          onRangeChangeListener.onPositionDrag(dragPosition);
        } else {
          onRangeChangeListener.onRangeDrag(getMinValue(), getMaxValue(), getDuration(), dragThumb);
          setOutputQuality(onRangeChangeListener.getQuality(getClipDuration(), getDuration()));
        }
      }
      return true;
    }

    if (actionMasked == MotionEvent.ACTION_UP) {
      if (onRangeChangeListener != null) {
        if (dragThumb == Thumb.POSITION) {
          onRangeChangeListener.onEndPositionDrag(dragPosition);
        } else {
          onRangeChangeListener.onRangeDragEnd(getMinValue(), getMaxValue(), getDuration(), dragThumb);
          setOutputQuality(onRangeChangeListener.getQuality(getClipDuration(), getDuration()));
        }
        lastDragThumb = dragThumb;
        dragEndTimeMs = System.currentTimeMillis();
        dragThumb     = null;
        invalidate();
      }
      return true;
    }

    if (actionMasked == MotionEvent.ACTION_CANCEL) {
      dragThumb = null;
    }

    return true;
  }

  private void setOutputQuality(@Nullable Quality outputQuality) {
    if (!Objects.equals(this.outputQuality, outputQuality)) {
      if (this.outputQuality == null) {
        qualityAvailableTimeMs = System.currentTimeMillis();
      }
      this.outputQuality = outputQuality;
      invalidate();
    }
  }

  private @Nullable Thumb closestThumb(@Px float x) {
    float midPoint = (right + left) / 2f;
    Thumb possibleThumb = x < midPoint ? Thumb.MIN : Thumb.MAX;
    int possibleThumbX = x < midPoint ? left : right;

    if (Math.abs(x - possibleThumbX) < thumbTouchRadius) {
      return possibleThumb;
    }

    return null;
  }

  private long pixelToDuration(float pixel) {
    return (long) (pixel / getDrawableWidth() * getDuration());
  }

  private int getDrawableWidth() {
    return getWidth() - getPaddingLeft() - getPaddingRight();
  }

  private int getDrawableHeight() {
    return getHeight() - getPaddingBottom() - getPaddingTop();
  }

  public void setRange(long minValue, long maxValue) {
    if (getDuration() > 0) {
      setMinMax(minValue, maxValue, Thumb.MIN);
      setMinMax(minValue, maxValue, Thumb.MAX);
    } else {
      externalMinValue = minValue;
      externalMaxValue = maxValue;
    }
  }

  public void setTimeLimit(int t, @NonNull TimeUnit timeUnit) {
    maximumSelectableRangeMicros = timeUnit.toMicros(t);
  }

  public enum Thumb {
    MIN,
    MAX,
    POSITION
  }

  public interface OnRangeChangeListener {

    void onPositionDrag(long position);

    void onEndPositionDrag(long position);

    void onRangeDrag(long minValue, long maxValue, long duration, Thumb thumb);

    void onRangeDragEnd(long minValue, long maxValue, long duration, Thumb thumb);

    @Nullable Quality getQuality(long clipDurationUs, long totalDurationUs);
  }

  public static final class Quality {
    private final long fileSize;
    private final int  qualityRange;

    public Quality(long fileSize, int qualityRange) {
      this.fileSize     = fileSize;
      this.qualityRange = qualityRange;
    }

    @Override public boolean equals(Object o) {
      if (!(o instanceof Quality)) {
        return false;
      }

      final Quality quality = (Quality) o;

      return fileSize == quality.fileSize &&
      qualityRange == quality.qualityRange;
    }

    @Override public int hashCode() {
      int result = (int) (fileSize ^ (fileSize >>> 32));
      result = 31 * result + qualityRange;
      return result;
    }
  }
}
