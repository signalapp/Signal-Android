package org.thoughtcrime.securesms.mediasend;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;

public class CameraButtonView extends View {

  private enum CameraButtonMode { IMAGE, MIXED }

  private static final float        CAPTURE_ARC_STROKE_WIDTH       = 6f;
  private static final float        HALF_CAPTURE_ARC_STROKE_WIDTH  = CAPTURE_ARC_STROKE_WIDTH / 2;
  private static final float        PROGRESS_ARC_STROKE_WIDTH      = 12f;
  private static final float        HALF_PROGRESS_ARC_STROKE_WIDTH = PROGRESS_ARC_STROKE_WIDTH / 2;
  private static final float        MINIMUM_ALLOWED_ZOOM_STEP      = 0.005f;
  private static final float        DEADZONE_REDUCTION_PERCENT     = 0.35f;
  private static final int          DRAG_DISTANCE_MULTIPLIER       = 3;
  private static final Interpolator ZOOM_INTERPOLATOR              = new DecelerateInterpolator();

  private final @NonNull Paint outlinePaint    = outlinePaint();
  private final @NonNull Paint backgroundPaint = backgroundPaint();
  private final @NonNull Paint arcPaint        = arcPaint();
  private final @NonNull Paint recordPaint     = recordPaint();
  private final @NonNull Paint progressPaint   = progressPaint();

  private Animation growAnimation;
  private Animation shrinkAnimation;

  private boolean isRecordingVideo;
  private float   progressPercent = 0f;
  private float   latestIncrement = 0f;

  private @NonNull  CameraButtonMode     cameraButtonMode = CameraButtonMode.IMAGE;
  private @Nullable VideoCaptureListener videoCaptureListener;

  private final float imageCaptureSize;
  private final float recordSize;
  private final RectF progressRect   = new RectF();
  private final Rect deadzoneRect = new Rect();

  private final @NonNull OnLongClickListener internalLongClickListener = v -> {
    notifyVideoCaptureStarted();
    shrinkAnimation.cancel();
    setScaleX(1f);
    setScaleY(1f);
    isRecordingVideo = true;
    return true;
  };

  public CameraButtonView(@NonNull Context context) {
    this(context, null);
  }

  public CameraButtonView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, R.attr.camera_button_style);
  }

  public CameraButtonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CameraButtonView, defStyleAttr, 0);

    imageCaptureSize = a.getDimensionPixelSize(R.styleable.CameraButtonView_imageCaptureSize, -1);
    recordSize       = a.getDimensionPixelSize(R.styleable.CameraButtonView_recordSize, -1);
    a.recycle();

    initializeImageAnimations();
  }

  private static Paint recordPaint() {
    Paint recordPaint = new Paint();
    recordPaint.setColor(0xFFF44336);
    recordPaint.setAntiAlias(true);
    recordPaint.setStyle(Paint.Style.FILL);
    return recordPaint;
  }

  private static Paint outlinePaint() {
    Paint outlinePaint = new Paint();
    outlinePaint.setColor(0x26000000);
    outlinePaint.setAntiAlias(true);
    outlinePaint.setStyle(Paint.Style.STROKE);
    outlinePaint.setStrokeWidth(1.5f);
    return outlinePaint;
  }

  private static Paint backgroundPaint() {
    Paint backgroundPaint = new Paint();
    backgroundPaint.setColor(0x4CFFFFFF);
    backgroundPaint.setAntiAlias(true);
    backgroundPaint.setStyle(Paint.Style.FILL);
    return backgroundPaint;
  }

  private static Paint arcPaint() {
    Paint arcPaint = new Paint();
    arcPaint.setColor(0xFFFFFFFF);
    arcPaint.setAntiAlias(true);
    arcPaint.setStyle(Paint.Style.STROKE);
    arcPaint.setStrokeWidth(CAPTURE_ARC_STROKE_WIDTH);
    return arcPaint;
  }

  private static Paint progressPaint() {
    Paint progressPaint = new Paint();
    progressPaint.setColor(0xFFFFFFFF);
    progressPaint.setAntiAlias(true);
    progressPaint.setStyle(Paint.Style.STROKE);
    progressPaint.setStrokeWidth(PROGRESS_ARC_STROKE_WIDTH);
    progressPaint.setShadowLayer(4, 0, 2, 0x40000000);
    return progressPaint;
  }

  private void initializeImageAnimations() {
    shrinkAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.camera_capture_button_shrink);
    growAnimation   = AnimationUtils.loadAnimation(getContext(), R.anim.camera_capture_button_grow);

    shrinkAnimation.setFillAfter(true);
    shrinkAnimation.setFillEnabled(true);
    growAnimation.setFillAfter(true);
    growAnimation.setFillEnabled(true);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (isRecordingVideo) {
      drawForVideoCapture(canvas);
    } else {
      drawForImageCapture(canvas);
    }
  }

  private void drawForImageCapture(Canvas canvas) {
    float centerX = getWidth() / 2f;
    float centerY = getHeight() / 2f;

    float radius = imageCaptureSize / 2f;
    canvas.drawCircle(centerX, centerY, radius, backgroundPaint);
    canvas.drawCircle(centerX, centerY, radius, outlinePaint);
    canvas.drawCircle(centerX, centerY, radius - HALF_CAPTURE_ARC_STROKE_WIDTH, arcPaint);
  }

  private void drawForVideoCapture(Canvas canvas) {
    float centerX = getWidth() / 2f;
    float centerY = getHeight() / 2f;

    canvas.drawCircle(centerX, centerY, centerY, backgroundPaint);
    canvas.drawCircle(centerX, centerY, centerY, outlinePaint);

    canvas.drawCircle(centerX, centerY, recordSize / 2f, recordPaint);

    progressRect.top    = HALF_PROGRESS_ARC_STROKE_WIDTH;
    progressRect.left   = HALF_PROGRESS_ARC_STROKE_WIDTH;
    progressRect.right  = getWidth() - HALF_PROGRESS_ARC_STROKE_WIDTH;
    progressRect.bottom = getHeight() - HALF_PROGRESS_ARC_STROKE_WIDTH;

    canvas.drawArc(progressRect, 270f, 360f * progressPercent, false, progressPaint);
  }

  @Override
  public void setOnLongClickListener(@Nullable OnLongClickListener listener) {
    throw new IllegalStateException("Use setVideoCaptureListener instead");
  }

  public void setVideoCaptureListener(@Nullable VideoCaptureListener videoCaptureListener) {
    if (isRecordingVideo) throw new IllegalStateException("Cannot set video capture listener while recording");

    if (videoCaptureListener != null) {
      this.cameraButtonMode = CameraButtonMode.MIXED;
      this.videoCaptureListener = videoCaptureListener;
      super.setOnLongClickListener(internalLongClickListener);
    } else {
      this.cameraButtonMode = CameraButtonMode.IMAGE;
      this.videoCaptureListener = null;
      super.setOnLongClickListener(null);
    }
  }

  public void setProgress(float percentage) {
    progressPercent = Util.clamp(percentage, 0f, 1f);
    invalidate();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (cameraButtonMode == CameraButtonMode.IMAGE) {
      return handleImageModeTouchEvent(event);
    }

    boolean eventWasHandled = handleVideoModeTouchEvent(event);
    int     action          = event.getAction();

    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      isRecordingVideo = false;
    }

    return eventWasHandled;
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    getLocalVisibleRect(deadzoneRect);
    deadzoneRect.left   += (int) (getWidth()  * DEADZONE_REDUCTION_PERCENT / 2f);
    deadzoneRect.top    += (int) (getHeight() * DEADZONE_REDUCTION_PERCENT / 2f);
    deadzoneRect.right  -= (int) (getWidth()  * DEADZONE_REDUCTION_PERCENT / 2f);
    deadzoneRect.bottom -= (int) (getHeight() * DEADZONE_REDUCTION_PERCENT / 2f);
  }

  private boolean handleImageModeTouchEvent(MotionEvent event) {
    int action = event.getAction();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        if (isEnabled()) {
          startAnimation(shrinkAnimation);
          performClick();
        }
        return true;
      case MotionEvent.ACTION_UP:
        startAnimation(growAnimation);
        return true;
      default:
        return super.onTouchEvent(event);
    }
  }

  private boolean handleVideoModeTouchEvent(MotionEvent event) {
    int action = event.getAction();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        latestIncrement = 0f;
        if (isEnabled()) {
          startAnimation(shrinkAnimation);
        }
      case MotionEvent.ACTION_MOVE:
        if (isRecordingVideo && eventIsNotInsideDeadzone(event)) {

          float maxRange  = getHeight() * DRAG_DISTANCE_MULTIPLIER;
          float deltaY    = Math.abs(event.getY() - deadzoneRect.top);
          float increment = Math.min(1f, deltaY / maxRange);

          if (Math.abs(increment - latestIncrement) < MINIMUM_ALLOWED_ZOOM_STEP) {
            break;
          }

          latestIncrement = increment;
          notifyZoomPercent(ZOOM_INTERPOLATOR.getInterpolation(increment));
          invalidate();
        }
        break;
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        if (!isRecordingVideo) {
          startAnimation(growAnimation);
        }
        notifyVideoCaptureEnded();
        break;
    }

    return super.onTouchEvent(event);
  }

  private boolean eventIsNotInsideDeadzone(MotionEvent event) {
    return Math.round(event.getY()) < deadzoneRect.top;
  }

  private void notifyVideoCaptureStarted() {
    if (!isRecordingVideo && videoCaptureListener != null) {
      videoCaptureListener.onVideoCaptureStarted();
    }
  }

  private void notifyVideoCaptureEnded() {
    if (isRecordingVideo && videoCaptureListener != null) {
      videoCaptureListener.onVideoCaptureComplete();
    }
  }

  private void notifyZoomPercent(float percent) {
    if (isRecordingVideo && videoCaptureListener != null) {
      videoCaptureListener.onZoomIncremented(percent);
    }
  }

  interface VideoCaptureListener {
    void onVideoCaptureStarted();
    void onVideoCaptureComplete();
    void onZoomIncremented(float percent);
  }
}
