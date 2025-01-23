package org.thoughtcrime.securesms.mediasend;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.DimensionUnit;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

public class VideoCameraButtonView extends View {

  private static final float CAPTURE_ARC_STROKE_WIDTH       = 3.5f;
  private static final int   PROGRESS_ARC_STROKE_WIDTH      = 4;
  private static final float CAPTURE_FILL_PROTECTION_IN_PX  = DimensionUnit.DP.toPixels(20);
  private static final float STOP_ICON_CORNER_RADIUS        = 20f;

  private final @NonNull Paint backgroundPaint  = backgroundPaint();
  private final @NonNull Paint arcPaint         = arcPaint();
  private final @NonNull Paint recordPaint      = recordPaint();
  private final @NonNull Paint progressPaint    = progressPaint();
  private final @NonNull Paint captureFillPaint = captureFillPaint();

  private boolean isRecordingVideo = false;
  private float   progressPercent  = 0f;

  private @Nullable VideoCaptureListener videoCaptureListener;

  private final float recordSize;
  private final RectF progressRect = new RectF();
  private final RectF stopIconRect = new RectF();

  private final @NonNull OnClickListener startRecordingClickListener = v -> {
    notifyVideoCaptureStarted();
    setScaleX(1f);
    setScaleY(1f);
    isRecordingVideo = true;
  };

  private final @NonNull OnClickListener stopRecordingClickListener = v -> {
    notifyVideoCaptureEnded();
    setScaleX(1f);
    setScaleY(1f);
    isRecordingVideo = false;
  };

  public VideoCameraButtonView(@NonNull Context context) {
    this(context, null);
  }

  public VideoCameraButtonView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public VideoCameraButtonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CameraButtonView, defStyleAttr, 0);

    recordSize = a.getDimensionPixelSize(R.styleable.CameraButtonView_recordSize, -1);
    a.recycle();
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
    outlinePaint.setStrokeWidth(ViewUtil.dpToPx(4));
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
    arcPaint.setStrokeWidth(DimensionUnit.DP.toPixels(CAPTURE_ARC_STROKE_WIDTH));
    return arcPaint;
  }

  private static Paint progressPaint() {
    Paint progressPaint = new Paint();
    progressPaint.setColor(0xFFFFFFFF);
    progressPaint.setAntiAlias(true);
    progressPaint.setStyle(Paint.Style.STROKE);
    progressPaint.setStrokeWidth(ViewUtil.dpToPx(PROGRESS_ARC_STROKE_WIDTH));
    progressPaint.setShadowLayer(4, 0, 2, 0x40000000);
    return progressPaint;
  }

  private static Paint captureFillPaint() {
    Paint arcPaint = new Paint();
    arcPaint.setColor(0xFFFFFFFF);
    arcPaint.setAntiAlias(true);
    arcPaint.setStyle(Paint.Style.FILL);
    return arcPaint;
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);

    drawForVideoCapture(canvas);
  }

  private void drawForVideoCapture(Canvas canvas) {
    float centerX = getWidth() / 2f;
    float centerY = getHeight() / 2f;

    float radius = recordSize / 2f;
    canvas.drawCircle(centerX, centerY, radius, backgroundPaint);
    if (isRecordingVideo) {

      stopIconRect.left   = centerX - CAPTURE_FILL_PROTECTION_IN_PX;
      stopIconRect.top    = centerY - CAPTURE_FILL_PROTECTION_IN_PX;
      stopIconRect.right  = centerX + CAPTURE_FILL_PROTECTION_IN_PX;
      stopIconRect.bottom = centerY + CAPTURE_FILL_PROTECTION_IN_PX;

      canvas.drawRoundRect(stopIconRect, STOP_ICON_CORNER_RADIUS, STOP_ICON_CORNER_RADIUS, recordPaint);
    } else {
      canvas.drawCircle(centerX, centerY, radius, arcPaint);
      canvas.drawCircle(centerX, centerY, radius - CAPTURE_FILL_PROTECTION_IN_PX, captureFillPaint);
    }

    progressRect.left   = centerX - radius + (PROGRESS_ARC_STROKE_WIDTH);
    progressRect.top    = centerY - radius + (PROGRESS_ARC_STROKE_WIDTH);
    progressRect.right  = centerX + radius - (PROGRESS_ARC_STROKE_WIDTH);
    progressRect.bottom = centerY + radius - (PROGRESS_ARC_STROKE_WIDTH);

    canvas.drawArc(progressRect, 270f, 360f * progressPercent, false, progressPaint);
  }

  public void setVideoCaptureListener(@Nullable VideoCaptureListener videoCaptureListener) {
    if (isRecordingVideo) throw new IllegalStateException("Cannot set video capture listener while recording");

    if (videoCaptureListener != null) {
      this.videoCaptureListener = videoCaptureListener;
      setOnClickListener(startRecordingClickListener);
    }
  }

  public void setProgress(float percentage) {
    progressPercent = Util.clamp(percentage, 0f, 1f);
    invalidate();
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

  @Override public boolean performClick() {
    if (isRecordingVideo) {
      setOnClickListener(stopRecordingClickListener);
    } else {
      setOnClickListener(startRecordingClickListener);
    }
    return super.performClick();
  }
}
