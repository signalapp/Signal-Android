/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.camera.view;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCapture.OnImageCapturedCallback;
import androidx.camera.core.ImageCapture.OnImageSavedCallback;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Logger;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.VideoCapture.OnVideoSavedCallback;
import androidx.camera.core.impl.LensFacingConverter;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.core.impl.utils.futures.FutureCallback;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.Executor;

/**
 * A {@link View} that displays a preview of the camera with methods {@link
 * #takePicture(Executor, OnImageCapturedCallback)},
 * {@link #takePicture(ImageCapture.OutputFileOptions, Executor, OnImageSavedCallback)},
 * {@link #startRecording(File , Executor , OnVideoSavedCallback callback)}
 * and {@link #stopRecording()}.
 *
 * <p>Because the Camera is a limited resource and consumes a high amount of power, CameraView must
 * be opened/closed. CameraView will handle opening/closing automatically through use of a {@link
 * LifecycleOwner}. Use {@link #bindToLifecycle(LifecycleOwner)} to start the camera.
 */
@RequiresApi(21)
@SuppressLint("RestrictedApi")
public final class SignalCameraView extends FrameLayout {
  static final String TAG = SignalCameraView.class.getSimpleName();

  static final int INDEFINITE_VIDEO_DURATION = -1;
  static final int INDEFINITE_VIDEO_SIZE = -1;

  private static final String EXTRA_SUPER = "super";
  private static final String EXTRA_ZOOM_RATIO = "zoom_ratio";
  private static final String EXTRA_PINCH_TO_ZOOM_ENABLED = "pinch_to_zoom_enabled";
  private static final String EXTRA_FLASH = "flash";
  private static final String EXTRA_MAX_VIDEO_DURATION = "max_video_duration";
  private static final String EXTRA_MAX_VIDEO_SIZE = "max_video_size";
  private static final String EXTRA_SCALE_TYPE = "scale_type";
  private static final String EXTRA_CAMERA_DIRECTION = "camera_direction";
  private static final String EXTRA_CAPTURE_MODE = "captureMode";

  private static final int LENS_FACING_NONE = 0;
  private static final int LENS_FACING_FRONT = 1;
  private static final int LENS_FACING_BACK = 2;
  private static final int FLASH_MODE_AUTO = 1;
  private static final int FLASH_MODE_ON = 2;
  private static final int FLASH_MODE_OFF = 4;
  // For tap-to-focus
  private long mDownEventTimestamp;
  // For pinch-to-zoom
  private PinchToZoomGestureDetector mPinchToZoomGestureDetector;
  private boolean mIsPinchToZoomEnabled = true;
  SignalCameraXModule mCameraModule;
  private final DisplayManager.DisplayListener mDisplayListener =
      new DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
          mCameraModule.invalidateView();
        }
      };
  private PreviewView mPreviewView;
  // For accessibility event
  private MotionEvent mUpEvent;

  public SignalCameraView(@NonNull Context context) {
    this(context, null);
  }

  public SignalCameraView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SignalCameraView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context, attrs);
  }

  @RequiresApi(21)
  public SignalCameraView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
                          int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init(context, attrs);
  }

  /**
   * Binds control of the camera used by this view to the given lifecycle.
   *
   * <p>This links opening/closing the camera to the given lifecycle. The camera will not operate
   * unless this method is called with a valid {@link LifecycleOwner} that is not in the {@link
   * androidx.lifecycle.Lifecycle.State#DESTROYED} state. Call this method only once camera
   * permissions have been obtained.
   *
   * <p>Once the provided lifecycle has transitioned to a {@link
   * androidx.lifecycle.Lifecycle.State#DESTROYED} state, CameraView must be bound to a new
   * lifecycle through this method in order to operate the camera.
   *
   * @param lifecycleOwner The lifecycle that will control this view's camera
   * @throws IllegalArgumentException if provided lifecycle is in a {@link
   *                                  androidx.lifecycle.Lifecycle.State#DESTROYED} state.
   * @throws IllegalStateException    if camera permissions are not granted.
   */
  @RequiresPermission(permission.CAMERA)
  public void bindToLifecycle(@NonNull LifecycleOwner lifecycleOwner) {
    mCameraModule.bindToLifecycle(lifecycleOwner);
  }

  private void init(Context context, @Nullable AttributeSet attrs) {
    addView(mPreviewView = new PreviewView(getContext()), 0 /* view position */);
    mCameraModule = new SignalCameraXModule(this);

    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraView);
      setScaleType(
          PreviewView.ScaleType.fromId(
              a.getInteger(R.styleable.CameraView_scaleType,
                  getScaleType().getId())));
      setPinchToZoomEnabled(
          a.getBoolean(
              R.styleable.CameraView_pinchToZoomEnabled, isPinchToZoomEnabled()));
      setCaptureMode(
          CaptureMode.fromId(
              a.getInteger(R.styleable.CameraView_captureMode,
                  getCaptureMode().getId())));

      int lensFacing = a.getInt(R.styleable.CameraView_lensFacing, LENS_FACING_BACK);
      switch (lensFacing) {
        case LENS_FACING_NONE:
          setCameraLensFacing(null);
          break;
        case LENS_FACING_FRONT:
          setCameraLensFacing(CameraSelector.LENS_FACING_FRONT);
          break;
        case LENS_FACING_BACK:
          setCameraLensFacing(CameraSelector.LENS_FACING_BACK);
          break;
        default:
          // Unhandled event.
      }

      int flashMode = a.getInt(R.styleable.CameraView_flash, 0);
      switch (flashMode) {
        case FLASH_MODE_AUTO:
          setFlash(ImageCapture.FLASH_MODE_AUTO);
          break;
        case FLASH_MODE_ON:
          setFlash(ImageCapture.FLASH_MODE_ON);
          break;
        case FLASH_MODE_OFF:
          setFlash(ImageCapture.FLASH_MODE_OFF);
          break;
        default:
          // Unhandled event.
      }

      a.recycle();
    }

    if (getBackground() == null) {
      setBackgroundColor(0xFF111111);
    }

    mPinchToZoomGestureDetector = new PinchToZoomGestureDetector(context);
  }

  @Override
  @NonNull
  protected LayoutParams generateDefaultLayoutParams() {
    return new LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
  }

  @Override
  @NonNull
  protected Parcelable onSaveInstanceState() {
    // TODO(b/113884082): Decide what belongs here or what should be invalidated on
    // configuration
    // change
    Bundle state = new Bundle();
    state.putParcelable(EXTRA_SUPER, super.onSaveInstanceState());
    state.putInt(EXTRA_SCALE_TYPE, getScaleType().getId());
    state.putFloat(EXTRA_ZOOM_RATIO, getZoomRatio());
    state.putBoolean(EXTRA_PINCH_TO_ZOOM_ENABLED, isPinchToZoomEnabled());
    state.putString(EXTRA_FLASH, FlashModeConverter.nameOf(getFlash()));
    state.putLong(EXTRA_MAX_VIDEO_DURATION, getMaxVideoDuration());
    state.putLong(EXTRA_MAX_VIDEO_SIZE, getMaxVideoSize());
    if (getCameraLensFacing() != null) {
      state.putString(EXTRA_CAMERA_DIRECTION,
          LensFacingConverter.nameOf(getCameraLensFacing()));
    }
    state.putInt(EXTRA_CAPTURE_MODE, getCaptureMode().getId());
    return state;
  }

  @Override
  protected void onRestoreInstanceState(@Nullable Parcelable savedState) {
    // TODO(b/113884082): Decide what belongs here or what should be invalidated on
    // configuration
    // change
    if (savedState instanceof Bundle) {
      Bundle state = (Bundle) savedState;
      super.onRestoreInstanceState(state.getParcelable(EXTRA_SUPER));
      setScaleType(PreviewView.ScaleType.fromId(state.getInt(EXTRA_SCALE_TYPE)));
      setZoomRatio(state.getFloat(EXTRA_ZOOM_RATIO));
      setPinchToZoomEnabled(state.getBoolean(EXTRA_PINCH_TO_ZOOM_ENABLED));
      setFlash(FlashModeConverter.valueOf(state.getString(EXTRA_FLASH)));
      setMaxVideoDuration(state.getLong(EXTRA_MAX_VIDEO_DURATION));
      setMaxVideoSize(state.getLong(EXTRA_MAX_VIDEO_SIZE));
      String lensFacingString = state.getString(EXTRA_CAMERA_DIRECTION);
      setCameraLensFacing(
          TextUtils.isEmpty(lensFacingString)
              ? null
              : LensFacingConverter.valueOf(lensFacingString));
      setCaptureMode(CaptureMode.fromId(state.getInt(EXTRA_CAPTURE_MODE)));
    } else {
      super.onRestoreInstanceState(savedState);
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    DisplayManager dpyMgr =
        (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
    dpyMgr.registerDisplayListener(mDisplayListener, new Handler(Looper.getMainLooper()));
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    DisplayManager dpyMgr =
        (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
    dpyMgr.unregisterDisplayListener(mDisplayListener);
  }

  /**
   * Gets the {@link LiveData} of the underlying {@link PreviewView}'s
   * {@link PreviewView.StreamState}.
   *
   * @return A {@link LiveData} containing the {@link PreviewView.StreamState}. Apps can either
   * get current value by {@link LiveData#getValue()} or register a observer by
   * {@link LiveData#observe}.
   * @see PreviewView#getPreviewStreamState()
   */
  @NonNull
  public LiveData<PreviewView.StreamState> getPreviewStreamState() {
    return mPreviewView.getPreviewStreamState();
  }

  @NonNull
  PreviewView getPreviewView() {
    return mPreviewView;
  }

  // TODO(b/124269166): Rethink how we can handle permissions here.
  @SuppressLint("MissingPermission")
  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // Since bindToLifecycle will depend on the measured dimension, only call it when measured
    // dimension is not 0x0
    if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
      mCameraModule.bindToLifecycleAfterViewMeasured();
    }

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  // TODO(b/124269166): Rethink how we can handle permissions here.
  @SuppressLint("MissingPermission")
  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    // In case that the CameraView size is always set as 0x0, we still need to trigger to force
    // binding to lifecycle
    mCameraModule.bindToLifecycleAfterViewMeasured();

    mCameraModule.invalidateView();
    super.onLayout(changed, left, top, right, bottom);
  }

  /**
   * @return One of {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90}, {@link
   * Surface#ROTATION_180}, {@link Surface#ROTATION_270}.
   */
  int getDisplaySurfaceRotation() {
    Display display = getDisplay();

    // Null when the View is detached. If we were in the middle of a background operation,
    // better to not NPE. When the background operation finishes, it'll realize that the camera
    // was closed.
    if (display == null) {
      return 0;
    }

    return display.getRotation();
  }

  /**
   * Returns the scale type used to scale the preview.
   *
   * @return The current {@link PreviewView.ScaleType}.
   */
  @NonNull
  public PreviewView.ScaleType getScaleType() {
    return mPreviewView.getScaleType();
  }

  /**
   * Sets the view finder scale type.
   *
   * <p>This controls how the view finder should be scaled and positioned within the view.
   *
   * @param scaleType The desired {@link PreviewView.ScaleType}.
   */
  public void setScaleType(@NonNull PreviewView.ScaleType scaleType) {
    mPreviewView.setScaleType(scaleType);
  }

  /**
   * Returns the scale type used to scale the preview.
   *
   * @return The current {@link CaptureMode}.
   */
  @NonNull
  public CaptureMode getCaptureMode() {
    return mCameraModule.getCaptureMode();
  }

  /**
   * Sets the CameraView capture mode
   *
   * <p>This controls only image or video capture function is enabled or both are enabled.
   *
   * @param captureMode The desired {@link CaptureMode}.
   */
  public void setCaptureMode(@NonNull CaptureMode captureMode) {
    mCameraModule.setCaptureMode(captureMode);
  }

  /**
   * Returns the maximum duration of videos, or {@link #INDEFINITE_VIDEO_DURATION} if there is no
   * timeout.
   *
   * @hide Not currently implemented.
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  public long getMaxVideoDuration() {
    return mCameraModule.getMaxVideoDuration();
  }

  /**
   * Sets the maximum video duration before
   * {@link OnVideoSavedCallback#onVideoSaved(VideoCapture.OutputFileResults)} is called
   * automatically.
   * Use {@link #INDEFINITE_VIDEO_DURATION} to disable the timeout.
   */
  private void setMaxVideoDuration(long duration) {
    mCameraModule.setMaxVideoDuration(duration);
  }

  /**
   * Returns the maximum size of videos in bytes, or {@link #INDEFINITE_VIDEO_SIZE} if there is no
   * timeout.
   */
  private long getMaxVideoSize() {
    return mCameraModule.getMaxVideoSize();
  }

  /**
   * Sets the maximum video size in bytes before
   * {@link OnVideoSavedCallback#onVideoSaved(VideoCapture.OutputFileResults)}
   * is called automatically. Use {@link #INDEFINITE_VIDEO_SIZE} to disable the size restriction.
   */
  private void setMaxVideoSize(long size) {
    mCameraModule.setMaxVideoSize(size);
  }

  /**
   * Takes a picture, and calls {@link OnImageCapturedCallback#onCaptureSuccess(ImageProxy)}
   * once when done.
   *
   * @param executor The executor in which the callback methods will be run.
   * @param callback Callback which will receive success or failure callbacks.
   */
  public void takePicture(@NonNull Executor executor, @NonNull OnImageCapturedCallback callback) {
    mCameraModule.takePicture(executor, callback);
  }

  /**
   * Takes a picture and calls
   * {@link OnImageSavedCallback#onImageSaved(ImageCapture.OutputFileResults)} when done.
   *
   * <p> The value of {@link ImageCapture.Metadata#isReversedHorizontal()} in the
   * {@link ImageCapture.OutputFileOptions} will be overwritten based on camera direction. For
   * front camera, it will be set to true; for back camera, it will be set to false.
   *
   * @param outputFileOptions Options to store the newly captured image.
   * @param executor          The executor in which the callback methods will be run.
   * @param callback          Callback which will receive success or failure.
   */
  public void takePicture(@NonNull ImageCapture.OutputFileOptions outputFileOptions,
                          @NonNull Executor executor,
                          @NonNull OnImageSavedCallback callback) {
    mCameraModule.takePicture(outputFileOptions, executor, callback);
  }

  /**
   * Takes a video and calls the OnVideoSavedCallback when done.
   *
   * @param outputFileOptions     Options to store the newly captured video.
   * @param executor              The executor in which the callback methods will be run.
   * @param callback              Callback which will receive success or failure.
   */
  public void startRecording(@NonNull VideoCapture.OutputFileOptions outputFileOptions,
                             @NonNull Executor executor,
                             @NonNull OnVideoSavedCallback callback) {
    mCameraModule.startRecording(outputFileOptions, executor, callback);
  }

  /** Stops an in progress video. */
  public void stopRecording() {
    mCameraModule.stopRecording();
  }

  /** @return True if currently recording. */
  public boolean isRecording() {
    return mCameraModule.isRecording();
  }

  /**
   * Queries whether the current device has a camera with the specified direction.
   *
   * @return True if the device supports the direction.
   * @throws IllegalStateException if the CAMERA permission is not currently granted.
   */
  @RequiresPermission(permission.CAMERA)
  public boolean hasCameraWithLensFacing(@CameraSelector.LensFacing int lensFacing) {
    return mCameraModule.hasCameraWithLensFacing(lensFacing);
  }

  /**
   * Toggles between the primary front facing camera and the primary back facing camera.
   *
   * <p>This will have no effect if not already bound to a lifecycle via {@link
   * #bindToLifecycle(LifecycleOwner)}.
   */
  public void toggleCamera() {
    mCameraModule.toggleCamera();
  }

  /**
   * Sets the desired camera by specifying desired lensFacing.
   *
   * <p>This will choose the primary camera with the specified camera lensFacing.
   *
   * <p>If called before {@link #bindToLifecycle(LifecycleOwner)}, this will set the camera to be
   * used when first bound to the lifecycle. If the specified lensFacing is not supported by the
   * device, as determined by {@link #hasCameraWithLensFacing(int)}, the first supported
   * lensFacing will be chosen when {@link #bindToLifecycle(LifecycleOwner)} is called.
   *
   * <p>If called with {@code null} AFTER binding to the lifecycle, the behavior would be
   * equivalent to unbind the use cases without the lifecycle having to be destroyed.
   *
   * @param lensFacing The desired camera lensFacing.
   */
  public void setCameraLensFacing(@Nullable Integer lensFacing) {
    mCameraModule.setCameraLensFacing(lensFacing);
  }

  /** Returns the currently selected lensFacing. */
  @Nullable
  public Integer getCameraLensFacing() {
    return mCameraModule.getLensFacing();
  }

  /** Gets the active flash strategy. */
  @ImageCapture.FlashMode
  public int getFlash() {
    return mCameraModule.getFlash();
  }

  // Begin Signal Custom Code Block
  public boolean hasFlash() {
    return mCameraModule.hasFlash();
  }
  // End Signal Custom Code Block

  /** Sets the active flash strategy. */
  public void setFlash(@ImageCapture.FlashMode int flashMode) {
    mCameraModule.setFlash(flashMode);
  }

  private long delta() {
    return System.currentTimeMillis() - mDownEventTimestamp;
  }

  @Override
  public boolean onTouchEvent(@NonNull MotionEvent event) {
    // Disable pinch-to-zoom and tap-to-focus while the camera module is paused.
    if (mCameraModule.isPaused()) {
      return false;
    }
    // Only forward the event to the pinch-to-zoom gesture detector when pinch-to-zoom is
    // enabled.
    if (isPinchToZoomEnabled()) {
      mPinchToZoomGestureDetector.onTouchEvent(event);
    }
    if (event.getPointerCount() == 2 && isPinchToZoomEnabled() && isZoomSupported()) {
      return true;
    }

    // Camera focus
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        mDownEventTimestamp = System.currentTimeMillis();
        break;
      case MotionEvent.ACTION_UP:
        if (delta() < ViewConfiguration.getLongPressTimeout()
            && mCameraModule.isBoundToLifecycle()) {
          mUpEvent = event;
          performClick();
        }
        break;
      default:
        // Unhandled event.
        return false;
    }
    return true;
  }

  /**
   * Focus the position of the touch event, or focus the center of the preview for
   * accessibility events
   */
  @Override
  public boolean performClick() {
    super.performClick();

    final float x = (mUpEvent != null) ? mUpEvent.getX() : getX() + getWidth() / 2f;
    final float y = (mUpEvent != null) ? mUpEvent.getY() : getY() + getHeight() / 2f;
    mUpEvent = null;

    Camera camera = mCameraModule.getCamera();
    if (camera != null) {
      MeteringPointFactory pointFactory = mPreviewView.getMeteringPointFactory();
      float afPointWidth = 1.0f / 6.0f;  // 1/6 total area
      float aePointWidth = afPointWidth * 1.5f;
      MeteringPoint afPoint = pointFactory.createPoint(x, y, afPointWidth);
      MeteringPoint aePoint = pointFactory.createPoint(x, y, aePointWidth);

      ListenableFuture<FocusMeteringResult> future =
          camera.getCameraControl().startFocusAndMetering(
              new FocusMeteringAction.Builder(afPoint,
                  FocusMeteringAction.FLAG_AF).addPoint(aePoint,
                  FocusMeteringAction.FLAG_AE).build());
      Futures.addCallback(future, new FutureCallback<FocusMeteringResult>() {
        @Override
        public void onSuccess(@Nullable FocusMeteringResult result) {
        }

        @Override
        public void onFailure(Throwable t) {
          // Throw the unexpected error.
          throw new RuntimeException(t);
        }
      }, CameraXExecutors.directExecutor());

    } else {
      Logger.d(TAG, "cannot access camera");
    }

    return true;
  }

  float rangeLimit(float val, float max, float min) {
    return Math.min(Math.max(val, min), max);
  }

  /**
   * Returns whether the view allows pinch-to-zoom.
   *
   * @return True if pinch to zoom is enabled.
   */
  public boolean isPinchToZoomEnabled() {
    return mIsPinchToZoomEnabled;
  }

  /**
   * Sets whether the view should allow pinch-to-zoom.
   *
   * <p>When enabled, the user can pinch the camera to zoom in/out. This only has an effect if the
   * bound camera supports zoom.
   *
   * @param enabled True to enable pinch-to-zoom.
   */
  public void setPinchToZoomEnabled(boolean enabled) {
    mIsPinchToZoomEnabled = enabled;
  }

  /**
   * Returns the current zoom ratio.
   *
   * @return The current zoom ratio.
   */
  public float getZoomRatio() {
    return mCameraModule.getZoomRatio();
  }

  /**
   * Sets the current zoom ratio.
   *
   * <p>Valid zoom values range from {@link #getMinZoomRatio()} to {@link #getMaxZoomRatio()}.
   *
   * @param zoomRatio The requested zoom ratio.
   */
  public void setZoomRatio(float zoomRatio) {
    mCameraModule.setZoomRatio(zoomRatio);
  }

  /**
   * Returns the minimum zoom ratio.
   *
   * <p>For most cameras this should return a zoom ratio of 1. A zoom ratio of 1 corresponds to a
   * non-zoomed image.
   *
   * @return The minimum zoom ratio.
   */
  public float getMinZoomRatio() {
    return mCameraModule.getMinZoomRatio();
  }

  /**
   * Returns the maximum zoom ratio.
   *
   * <p>The zoom ratio corresponds to the ratio between both the widths and heights of a
   * non-zoomed image and a maximally zoomed image for the selected camera.
   *
   * @return The maximum zoom ratio.
   */
  public float getMaxZoomRatio() {
    return mCameraModule.getMaxZoomRatio();
  }

  /**
   * Returns whether the bound camera supports zooming.
   *
   * @return True if the camera supports zooming.
   */
  public boolean isZoomSupported() {
    return mCameraModule.isZoomSupported();
  }

  /**
   * Turns on/off torch.
   *
   * @param torch True to turn on torch, false to turn off torch.
   */
  public void enableTorch(boolean torch) {
    mCameraModule.enableTorch(torch);
  }

  /**
   * Returns current torch status.
   *
   * @return true if torch is on , otherwise false
   */
  public boolean isTorchOn() {
    return mCameraModule.isTorchOn();
  }

  /**
   * The capture mode used by CameraView.
   *
   * <p>This enum can be used to determine which capture mode will be enabled for {@link
   * SignalCameraView}.
   */
  public enum CaptureMode {
    /** A mode where image capture is enabled. */
    IMAGE(0),
    /** A mode where video capture is enabled. */
    VIDEO(1),
    /**
     * A mode where both image capture and video capture are simultaneously enabled. Note that
     * this mode may not be available on every device.
     */
    MIXED(2);

    private final int mId;

    int getId() {
      return mId;
    }

    CaptureMode(int id) {
      mId = id;
    }

    static CaptureMode fromId(int id) {
      for (CaptureMode f : values()) {
        if (f.mId == id) {
          return f;
        }
      }
      throw new IllegalArgumentException();
    }
  }

  static class S extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    private ScaleGestureDetector.OnScaleGestureListener mListener;

    void setRealGestureDetector(ScaleGestureDetector.OnScaleGestureListener l) {
      mListener = l;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
      return mListener.onScale(detector);
    }
  }

  private class PinchToZoomGestureDetector extends ScaleGestureDetector
      implements ScaleGestureDetector.OnScaleGestureListener {
    PinchToZoomGestureDetector(Context context) {
      this(context, new S());
    }

    PinchToZoomGestureDetector(Context context, S s) {
      super(context, s);
      s.setRealGestureDetector(this);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
      float scale = detector.getScaleFactor();

      // Speeding up the zoom by 2X.
      if (scale > 1f) {
        scale = 1.0f + (scale - 1.0f) * 2;
      } else {
        scale = 1.0f - (1.0f - scale) * 2;
      }

      float newRatio = getZoomRatio() * scale;
      newRatio = rangeLimit(newRatio, getMaxZoomRatio(), getMinZoomRatio());
      setZoomRatio(newRatio);
      return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
      return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
    }
  }
}