package org.thoughtcrime.securesms.video.videoconverter;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RequiresApi;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = 23)
public final class VideoThumbnailsRangeSelectorView extends VideoThumbnailsView {

  private static final String TAG = Log.tag(VideoThumbnailsRangeSelectorView.class);

  private static final long  MINIMUM_SELECTABLE_RANGE    = TimeUnit.MILLISECONDS.toMicros(500);
  private static final int   ANIMATION_DURATION_MS       = 100;
  private static final float THUMB_RECT_CORNER_RADIUS    = ViewUtil.dpToPx(4);
  private static final float ACTIVE_REGION_CORNER_RADIUS = ViewUtil.dpToPx(8);

  private final Paint    paint                    = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint    paintGrey                = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint    thumbTimeTextPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint    thumbTimeBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect     tempDrawRect             = new Rect();
  private final RectF    timePillRect             = new RectF();
  private final Path     activeRegionPath         = new Path();

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
            private PositionDragListener  playerOnRangeChangeListener;
            private RangeDragListener     editorOnRangeChangeListener;
  @Px       private int                   thumbSizePixels;
  @Px       private int                   thumbTouchRadius;
  @ColorInt private int                   thumbColor;
            private long                  actualPosition;
            private long                  dragPosition;
  @Px       private int                   thumbHintTextSize;
  @ColorInt private int                   thumbHintTextColor;
  @ColorInt private int                   thumbHintBackgroundColor;
            private long                  dragStartTimeMs;
            private long                  dragEndTimeMs;
            private long                  maximumSelectableRangeMicros;

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
        thumbColor               = typedArray.getColor(R.styleable.VideoThumbnailsRangeSelectorView_thumbColor, 0xffff0000);
        thumbTouchRadius         = typedArray.getDimensionPixelSize(R.styleable.VideoThumbnailsRangeSelectorView_thumbTouchRadius, 50);
        thumbHintTextSize        = typedArray.getDimensionPixelSize(R.styleable.VideoThumbnailsRangeSelectorView_thumbHintTextSize, 0);
        thumbHintTextColor       = typedArray.getColor(R.styleable.VideoThumbnailsRangeSelectorView_thumbHintTextColor, 0xffff0000);
        thumbHintBackgroundColor = typedArray.getColor(R.styleable.VideoThumbnailsRangeSelectorView_thumbHintBackgroundColor, 0xff00ff00);
      } finally {
        typedArray.recycle();
      }
    }

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
     maxValue = duration;

    if (duration > 0) {
      if (externalMaxValue != null) {
        setMinMax(getMinValue(), externalMaxValue, Thumb.MAX);
        externalMaxValue = null;
      }

      if (externalMinValue != null) {
        setMinMax(externalMinValue, getMaxValue(), Thumb.MIN);
        externalMinValue = null;
      }
    }

    onRangeDrag(getMinValue(), getMaxValue(), duration, true);

    invalidate();
  }

  public void registerPlayerOnRangeChangeListener(PositionDragListener playerOnRangeChangeListener) {
    this.playerOnRangeChangeListener = playerOnRangeChangeListener;
  }

  public void registerEditorOnRangeChangeListener(RangeDragListener editorOnRangeChangeListener) {
    this.editorOnRangeChangeListener = editorOnRangeChangeListener;
  }

  public void unregisterPlayerOnRangeChangeListener() {
    this.playerOnRangeChangeListener = null;
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

    int drawableWidth  = getDrawableWidth();
    int drawableHeight = getDrawableHeight();

    long duration = getDuration();

    long min = getMinValue();
    long max = getMaxValue();

    long drawPosAt = dragThumb == Thumb.POSITION ? dragPosition : actualPosition;

    left   = duration != 0 ? (int) ((min * drawableWidth) / duration) : 0;
    right  = duration != 0 ? (int) ((max * drawableWidth) / duration) : drawableWidth;
    cursor = duration != 0 ? (int) ((drawPosAt * drawableWidth) / duration) : drawableWidth;

    canvas.save();
    canvas.clipPath(clippingPath);
    canvas.translate(getPaddingLeft(), getPaddingTop());

    // draw greyed out areas
    if (Build.VERSION.SDK_INT >= 26) {
      activeRegionPath.reset();
      timePillRect.set(left + 1, 0, right - 1, drawableHeight);
      activeRegionPath.addRoundRect(timePillRect, ACTIVE_REGION_CORNER_RADIUS, ACTIVE_REGION_CORNER_RADIUS, Path.Direction.CW);
      canvas.clipOutPath(activeRegionPath);
      tempDrawRect.set(0, 0, drawableWidth, drawableHeight);
      canvas.drawRect(tempDrawRect, paintGrey);
    } else {
      tempDrawRect.set(0, 0, left - 1, drawableHeight);
      canvas.drawRect(tempDrawRect, paintGrey);
      tempDrawRect.set(right + 1, 0, drawableWidth, drawableHeight);
      canvas.drawRect(tempDrawRect, paintGrey);
    }

    canvas.restore();

    canvas.translate(getPaddingLeft(), getPaddingTop());

    int verticalThumbInset = drawableHeight / 4;
    int halfThumbWidth     = thumbSizePixels / 2;
    // draw thumb rectangles
    paint.setStyle(Paint.Style.FILL_AND_STROKE);
    paint.setColor(thumbColor);
    timePillRect.set(left - halfThumbWidth, verticalThumbInset, left + halfThumbWidth, drawableHeight - verticalThumbInset);
    canvas.drawRoundRect(timePillRect, THUMB_RECT_CORNER_RADIUS, THUMB_RECT_CORNER_RADIUS, paint);
    timePillRect.set(right - halfThumbWidth, verticalThumbInset, right + halfThumbWidth, drawableHeight - verticalThumbInset);
    canvas.drawRoundRect(timePillRect, THUMB_RECT_CORNER_RADIUS, THUMB_RECT_CORNER_RADIUS, paint);

    // draw time hint pill
    if (thumbHintTextSize > 0) {
      if (dragStartTimeMs > 0 && (dragThumb == Thumb.MIN || dragThumb == Thumb.MAX)) {
        drawTimeHint(canvas, drawableWidth, dragThumb, false);
      }
      if (dragEndTimeMs > 0 && (lastDragThumb == Thumb.MIN || lastDragThumb == Thumb.MAX)) {
        drawTimeHint(canvas, drawableWidth, lastDragThumb, true);
      }
    }

    // draw current position marker
    if (left <= cursor && cursor <= right && dragThumb != Thumb.MIN && dragThumb != Thumb.MAX) {
      timePillRect.set(cursor - halfThumbWidth, 0, cursor + halfThumbWidth, drawableHeight);
      paint.setStyle(Paint.Style.FILL_AND_STROKE);
      paint.setColor(thumbColor);
      canvas.drawRoundRect(timePillRect, THUMB_RECT_CORNER_RADIUS, THUMB_RECT_CORNER_RADIUS, paint);
    }
  }

  private void drawTimeHint(Canvas canvas, int drawableWidth, Thumb dragThumb, boolean fadeOut) {
    canvas.save();
    long   microsecondValue = dragThumb == Thumb.MIN ? getMinValue() : getMaxValue();
    long   seconds          = TimeUnit.MICROSECONDS.toSeconds(microsecondValue);
    String timeString       = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    float  topBottomPadding = thumbHintTextSize * 0.5f;
    float  leftRightPadding = thumbHintTextSize * 0.75f;

    thumbTimeTextPaint.getTextBounds(timeString, 0, timeString.length(), tempDrawRect);

    timePillRect.set(tempDrawRect.left - leftRightPadding, tempDrawRect.top - topBottomPadding, tempDrawRect.right + leftRightPadding, tempDrawRect.bottom + topBottomPadding);

    float halfPillWidth  = timePillRect.width() / 2f;
    float halfPillHeight = timePillRect.height() / 2f;

    long animationTime = fadeOut ? ANIMATION_DURATION_MS - Math.min(ANIMATION_DURATION_MS, System.currentTimeMillis() - dragEndTimeMs)
                                 : Math.min(ANIMATION_DURATION_MS, System.currentTimeMillis() - dragStartTimeMs);
    float animationPosition = animationTime / (float) ANIMATION_DURATION_MS;
    float scaleIn           = 0.2f * animationPosition + 0.8f;
    int   alpha             = (int) (255 * animationPosition);

    if (dragThumb == Thumb.MAX) {
      canvas.translate(Math.min(right, drawableWidth - halfPillWidth), 0);
    } else {
      canvas.translate(Math.max(left, halfPillWidth), 0);
    }

    float timePillOffset = timePillRect.height() * -1.5f;
    canvas.translate(0, timePillOffset);
    canvas.scale(scaleIn, scaleIn);
    thumbTimeTextPaint.setAlpha(alpha);
    thumbTimeBackgroundPaint.setAlpha(alpha);
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

  public long getMinValue() {
    return minValue == null ? 0 : minValue;
  }

  public long getMaxValue() {
    return maxValue == null ? getDuration() : maxValue;
  }

  private boolean setMinValue(long minValue) {
    if (this.minValue == null || this.minValue != minValue) {
      return setMinMax(minValue, getMaxValue(), Thumb.MIN);
    } else {
      return false;
    }
  }

  private boolean setMaxValue(long maxValue) {
    if (this.maxValue == null || this.maxValue != maxValue) {
      return setMinMax(getMinValue(), maxValue, Thumb.MAX);
    } else {
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
      long    delta   = pixelToDuration(event.getX() - xDown);
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
      if (changed) {
        if (dragThumb == Thumb.POSITION) {
          onPositionDrag(dragPosition);
        } else {
          onRangeDrag(getMinValue(), getMaxValue(), getDuration(), false);
        }
      }
      return true;
    }

    if (actionMasked == MotionEvent.ACTION_UP) {
      if (editorOnRangeChangeListener != null) {
        if (dragThumb == Thumb.POSITION) {
          onEndPositionDrag(dragPosition);
        } else {
          onRangeDrag(getMinValue(), getMaxValue(), getDuration(), true);
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

  private @Nullable Thumb closestThumb(@Px float x) {
    float midPoint       = (right + left) / 2f;
    Thumb possibleThumb  = x < midPoint ? Thumb.MIN : Thumb.MAX;
    int   possibleThumbX = x < midPoint ? left : right;

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
    } else {
      externalMinValue = minValue;
      externalMaxValue = maxValue;
    }
  }

  public void setTimeLimit(int t, @NonNull TimeUnit timeUnit) {
    maximumSelectableRangeMicros = timeUnit.toMicros(t);
  }

  private void onPositionDrag(long position) {
    if (playerOnRangeChangeListener != null) {
      playerOnRangeChangeListener.onPositionDrag(position);
    }
  }

  private void onEndPositionDrag(long position) {
    if (playerOnRangeChangeListener != null) {
      playerOnRangeChangeListener.onEndPositionDrag(position);
    }
  }

  private void onRangeDrag(long minValue, long maxValue, long duration, boolean end) {
    if (editorOnRangeChangeListener != null) {
      editorOnRangeChangeListener.onRangeDrag(minValue, maxValue, duration, end);
    }
  }

  public enum Thumb {
    MIN,
    MAX,
    POSITION
  }

  public interface PositionDragListener {
    void onPositionDrag(long position);
    void onEndPositionDrag(long position);
  }

  public interface RangeDragListener {
    void onRangeDrag(long minValue, long maxValue, long duration, boolean start);
  }
}
