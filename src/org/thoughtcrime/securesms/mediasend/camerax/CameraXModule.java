package org.thoughtcrime.securesms.mediasend.camerax;

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

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Looper;
import android.util.Log;
import android.util.Rational;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.annotation.UiThread;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraOrientationUtil;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.FlashMode;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCapture.OnImageCapturedListener;
import androidx.camera.core.ImageCapture.OnImageSavedListener;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.VideoCapture.OnVideoSavedListener;
import androidx.camera.core.VideoCaptureConfig;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;

import org.thoughtcrime.securesms.mediasend.camerax.CameraXView.CaptureMode;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** CameraX use case operation built on @{link androidx.camera.core}. */
@RequiresApi(21)
@SuppressLint("RestrictedApi")
final class CameraXModule {
  public static final String TAG = "CameraXModule";

  private static final int MAX_VIEW_DIMENSION = 2000;
  private static final float UNITY_ZOOM_SCALE = 1f;
  private static final float ZOOM_NOT_SUPPORTED = UNITY_ZOOM_SCALE;
  private static final Rational ASPECT_RATIO_16_9 = new Rational(16, 9);
  private static final Rational ASPECT_RATIO_4_3 = new Rational(4, 3);
  private static final Rational ASPECT_RATIO_9_16 = new Rational(9, 16);
  private static final Rational ASPECT_RATIO_3_4 = new Rational(3, 4);

  private final CameraManager mCameraManager;
  private final PreviewConfig.Builder mPreviewConfigBuilder;
  private final VideoCaptureConfig.Builder mVideoCaptureConfigBuilder;
  private final ImageCaptureConfig.Builder mImageCaptureConfigBuilder;
  private final CameraXView mCameraXView;
  final AtomicBoolean mVideoIsRecording = new AtomicBoolean(false);
  private CaptureMode mCaptureMode = CaptureMode.IMAGE;
  private long mMaxVideoDuration = CameraXView.INDEFINITE_VIDEO_DURATION;
  private long mMaxVideoSize = CameraXView.INDEFINITE_VIDEO_SIZE;
  private FlashMode mFlash = FlashMode.OFF;
  @Nullable
  private ImageCapture mImageCapture;
  @Nullable
  private VideoCapture mVideoCapture;
  @Nullable
  Preview mPreview;
  @Nullable
  LifecycleOwner mCurrentLifecycle;
  private final LifecycleObserver mCurrentLifecycleObserver =
      new LifecycleObserver() {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        public void onDestroy(LifecycleOwner owner) {
          if (owner == mCurrentLifecycle) {
            clearCurrentLifecycle();
            mPreview.removePreviewOutputListener();
          }
        }
      };
  @Nullable
  private LifecycleOwner mNewLifecycle;
  private float mZoomLevel = UNITY_ZOOM_SCALE;
  @Nullable
  private Rect mCropRegion;
  @Nullable
  private CameraX.LensFacing mCameraLensFacing = LensFacing.BACK;

  CameraXModule(CameraXView view) {
    this.mCameraXView = view;

    mCameraManager = (CameraManager) view.getContext().getSystemService(Context.CAMERA_SERVICE);

    mPreviewConfigBuilder = new PreviewConfig.Builder().setTargetName("Preview");

    mImageCaptureConfigBuilder =
        new ImageCaptureConfig.Builder().setTargetName("ImageCapture");

    mVideoCaptureConfigBuilder =
        new VideoCaptureConfig.Builder().setTargetName("VideoCapture");
  }

  /**
   * Rescales view rectangle with dimensions in [-1000, 1000] to a corresponding rectangle in the
   * sensor coordinate frame.
   */
  private static Rect rescaleViewRectToSensorRect(Rect view, Rect sensor) {
    // Scale width and height.
    int newWidth = Math.round(view.width() * sensor.width() / (float) MAX_VIEW_DIMENSION);
    int newHeight = Math.round(view.height() * sensor.height() / (float) MAX_VIEW_DIMENSION);

    // Scale top/left corner.
    int halfViewDimension = MAX_VIEW_DIMENSION / 2;
    int leftOffset =
        Math.round(
            (view.left + halfViewDimension)
                * sensor.width()
                / (float) MAX_VIEW_DIMENSION)
            + sensor.left;
    int topOffset =
        Math.round(
            (view.top + halfViewDimension)
                * sensor.height()
                / (float) MAX_VIEW_DIMENSION)
            + sensor.top;

    // Now, produce the scaled rect.
    Rect scaled = new Rect();
    scaled.left = leftOffset;
    scaled.top = topOffset;
    scaled.right = scaled.left + newWidth;
    scaled.bottom = scaled.top + newHeight;
    return scaled;
  }

  @RequiresPermission(permission.CAMERA)
  public void bindToLifecycle(LifecycleOwner lifecycleOwner) {
    mNewLifecycle = lifecycleOwner;

    if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
      bindToLifecycleAfterViewMeasured();
    }
  }

  @RequiresPermission(permission.CAMERA)
  void bindToLifecycleAfterViewMeasured() {
    if (mNewLifecycle == null) {
      return;
    }

    clearCurrentLifecycle();
    mCurrentLifecycle = mNewLifecycle;
    mNewLifecycle = null;
    if (mCurrentLifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
      mCurrentLifecycle = null;
      throw new IllegalArgumentException("Cannot bind to lifecycle in a destroyed state.");
    }

    final int cameraOrientation;
    try {
      String cameraId;
      Set<LensFacing> available = getAvailableCameraLensFacing();

      if (available.isEmpty()) {
        Log.w(TAG, "Unable to bindToLifeCycle since no cameras available");
        mCameraLensFacing = null;
      }

      // Ensure the current camera exists, or default to another camera
      if (mCameraLensFacing != null && !available.contains(mCameraLensFacing)) {
        Log.w(TAG, "Camera does not exist with direction " + mCameraLensFacing);

        // Default to the first available camera direction
        mCameraLensFacing = available.iterator().next();

        Log.w(TAG, "Defaulting to primary camera with direction " + mCameraLensFacing);
      }

      // Do not attempt to create use cases for a null cameraLensFacing. This could occur if
      // the
      // user explicitly sets the LensFacing to null, or if we determined there
      // were no available cameras, which should be logged in the logic above.
      if (mCameraLensFacing == null) {
        return;
      }

      cameraId = CameraX.getCameraWithLensFacing(mCameraLensFacing);
      if (cameraId == null) {
        return;
      }
      CameraInfo cameraInfo = CameraX.getCameraInfo(cameraId);
      cameraOrientation = cameraInfo.getSensorRotationDegrees();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to bind to lifecycle.", e);
    }

    // Set the preferred aspect ratio as 4:3 if it is IMAGE only mode. Set the preferred aspect
    // ratio as 16:9 if it is VIDEO or MIXED mode. Then, it will be WYSIWYG when the view finder
    // is
    // in CENTER_INSIDE mode.

    boolean isDisplayPortrait = getDisplayRotationDegrees() == 0
        || getDisplayRotationDegrees() == 180;

    if (getCaptureMode() == CaptureMode.IMAGE) {
      mImageCaptureConfigBuilder.setTargetAspectRatio(
          isDisplayPortrait ? ASPECT_RATIO_3_4 : ASPECT_RATIO_4_3);
      mPreviewConfigBuilder.setTargetAspectRatio(
          isDisplayPortrait ? ASPECT_RATIO_3_4 : ASPECT_RATIO_4_3);
    } else {
      mImageCaptureConfigBuilder.setTargetAspectRatio(
          isDisplayPortrait ? ASPECT_RATIO_9_16 : ASPECT_RATIO_16_9);
      mPreviewConfigBuilder.setTargetAspectRatio(
          isDisplayPortrait ? ASPECT_RATIO_9_16 : ASPECT_RATIO_16_9);
    }

    mImageCaptureConfigBuilder.setTargetRotation(getDisplaySurfaceRotation());
    mImageCaptureConfigBuilder.setLensFacing(mCameraLensFacing);
    mImageCaptureConfigBuilder.setCaptureMode(CameraXUtil.getOptimalCaptureMode());
    mImageCaptureConfigBuilder.setTargetResolution(new Size(1920, 1080));
    mImageCapture = new ImageCapture(mImageCaptureConfigBuilder.build());

    mVideoCaptureConfigBuilder.setTargetRotation(getDisplaySurfaceRotation());
    mVideoCaptureConfigBuilder.setLensFacing(mCameraLensFacing);
    mVideoCapture = new VideoCapture(mVideoCaptureConfigBuilder.build());
    mPreviewConfigBuilder.setLensFacing(mCameraLensFacing);

    int relativeCameraOrientation = getRelativeCameraOrientation(false);

    if (relativeCameraOrientation == 90 || relativeCameraOrientation == 270) {
      mPreviewConfigBuilder.setTargetResolution(
          new Size(getMeasuredHeight(), getMeasuredWidth()));
    } else {
      mPreviewConfigBuilder.setTargetResolution(
          new Size(getMeasuredWidth(), getMeasuredHeight()));
    }

    mPreview = new Preview(mPreviewConfigBuilder.build());
    mPreview.setOnPreviewOutputUpdateListener(
        new Preview.OnPreviewOutputUpdateListener() {
          @Override
          public void onUpdated(Preview.PreviewOutput output) {
            boolean needReverse = cameraOrientation != 0 && cameraOrientation != 180;
            int textureWidth =
                needReverse
                    ? output.getTextureSize().getHeight()
                    : output.getTextureSize().getWidth();
            int textureHeight =
                needReverse
                    ? output.getTextureSize().getWidth()
                    : output.getTextureSize().getHeight();
            CameraXModule.this.onPreviewSourceDimensUpdated(textureWidth,
                textureHeight);
            CameraXModule.this.setSurfaceTexture(output.getSurfaceTexture());
          }
        });

    if (getCaptureMode() == CaptureMode.IMAGE) {
      CameraX.bindToLifecycle(mCurrentLifecycle, mImageCapture, mPreview);
    } else if (getCaptureMode() == CaptureMode.VIDEO) {
      CameraX.bindToLifecycle(mCurrentLifecycle, mVideoCapture, mPreview);
    } else {
      CameraX.bindToLifecycle(mCurrentLifecycle, mImageCapture, mVideoCapture, mPreview);
    }
    setZoomLevel(mZoomLevel);
    mCurrentLifecycle.getLifecycle().addObserver(mCurrentLifecycleObserver);
    // Enable flash setting in ImageCapture after use cases are created and binded.
    setFlash(getFlash());
  }

  public void open() {
    throw new UnsupportedOperationException(
        "Explicit open/close of camera not yet supported. Use bindtoLifecycle() instead.");
  }

  public void close() {
    throw new UnsupportedOperationException(
        "Explicit open/close of camera not yet supported. Use bindtoLifecycle() instead.");
  }

  public void stopPreview() {
    if (mPreview != null) {
      mPreview.clear();
    }
  }

  public void takePicture(OnImageCapturedListener listener) {
    if (mImageCapture == null) {
      return;
    }

    if (getCaptureMode() == CaptureMode.VIDEO) {
      throw new IllegalStateException("Can not take picture under VIDEO capture mode.");
    }

    if (listener == null) {
      throw new IllegalArgumentException("OnImageCapturedListener should not be empty");
    }

    mImageCapture.takePicture(listener);
  }

  public void takePicture(File saveLocation, OnImageSavedListener listener) {
    if (mImageCapture == null) {
      return;
    }

    if (getCaptureMode() == CaptureMode.VIDEO) {
      throw new IllegalStateException("Can not take picture under VIDEO capture mode.");
    }

    if (listener == null) {
      throw new IllegalArgumentException("OnImageSavedListener should not be empty");
    }

    ImageCapture.Metadata metadata = new ImageCapture.Metadata();
    metadata.isReversedHorizontal = mCameraLensFacing == LensFacing.FRONT;
    mImageCapture.takePicture(saveLocation, listener, metadata);
  }

  public void startRecording(File file, final OnVideoSavedListener listener) {
    if (mVideoCapture == null) {
      return;
    }

    if (getCaptureMode() == CaptureMode.IMAGE) {
      throw new IllegalStateException("Can not record video under IMAGE capture mode.");
    }

    if (listener == null) {
      throw new IllegalArgumentException("OnVideoSavedListener should not be empty");
    }

    mVideoIsRecording.set(true);
    mVideoCapture.startRecording(
        file,
        new VideoCapture.OnVideoSavedListener() {
          @Override
          public void onVideoSaved(File savedFile) {
            mVideoIsRecording.set(false);
            listener.onVideoSaved(savedFile);
          }

          @Override
          public void onError(
              VideoCapture.UseCaseError useCaseError,
              String message,
              @Nullable Throwable cause) {
            mVideoIsRecording.set(false);
            Log.e(TAG, message, cause);
            listener.onError(useCaseError, message, cause);
          }
        });
  }

  public void stopRecording() {
    if (mVideoCapture == null) {
      return;
    }

    mVideoCapture.stopRecording();
  }

  public boolean isRecording() {
    return mVideoIsRecording.get();
  }

  // TODO(b/124269166): Rethink how we can handle permissions here.
  @SuppressLint("MissingPermission")
  public void setCameraLensFacing(@Nullable LensFacing lensFacing) {
    // Setting same lens facing is a no-op, so check for that first
    if (mCameraLensFacing != lensFacing) {
      // If we're not bound to a lifecycle, just update the camera that will be opened when we
      // attach to a lifecycle.
      mCameraLensFacing = lensFacing;

      if (mCurrentLifecycle != null) {
        // Re-bind to lifecycle with new camera
        bindToLifecycle(mCurrentLifecycle);
      }
    }
  }

  @RequiresPermission(permission.CAMERA)
  public boolean hasCameraWithLensFacing(LensFacing lensFacing) {
    String cameraId;
    try {
      cameraId = CameraX.getCameraWithLensFacing(lensFacing);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to query lens facing.", e);
    }

    return cameraId != null;
  }

  @Nullable
  public LensFacing getLensFacing() {
    return mCameraLensFacing;
  }

  public void toggleCamera() {
    // TODO(b/124269166): Rethink how we can handle permissions here.
    @SuppressLint("MissingPermission")
    Set<LensFacing> availableCameraLensFacing = getAvailableCameraLensFacing();

    if (availableCameraLensFacing.isEmpty()) {
      return;
    }

    if (mCameraLensFacing == null) {
      setCameraLensFacing(availableCameraLensFacing.iterator().next());
      return;
    }

    if (mCameraLensFacing == LensFacing.BACK
        && availableCameraLensFacing.contains(LensFacing.FRONT)) {
      setCameraLensFacing(LensFacing.FRONT);
      return;
    }

    if (mCameraLensFacing == LensFacing.FRONT
        && availableCameraLensFacing.contains(LensFacing.BACK)) {
      setCameraLensFacing(LensFacing.BACK);
      return;
    }
  }

  public void focus(Rect focus, Rect metering) {
    if (mPreview == null) {
      // Nothing to focus on since we don't yet have a preview
      return;
    }

    Rect rescaledFocus;
    Rect rescaledMetering;
    try {
      Rect sensorRegion;
      if (mCropRegion != null) {
        sensorRegion = mCropRegion;
      } else {
        sensorRegion = getSensorSize(getActiveCamera());
      }
      rescaledFocus = rescaleViewRectToSensorRect(focus, sensorRegion);
      rescaledMetering = rescaleViewRectToSensorRect(metering, sensorRegion);
    } catch (Exception e) {
      Log.e(TAG, "Failed to rescale the focus and metering rectangles.", e);
      return;
    }

    mPreview.focus(rescaledFocus, rescaledMetering);
  }

  public float getZoomLevel() {
    return mZoomLevel;
  }

  public void setZoomLevel(float zoomLevel) {
    // Set the zoom level in case it is set before binding to a lifecycle
    this.mZoomLevel = zoomLevel;

    if (mPreview == null) {
      // Nothing to zoom on yet since we don't have a preview. Defer calculating crop
      // region.
      return;
    }

    Rect sensorSize;
    try {
      sensorSize = getSensorSize(getActiveCamera());
      if (sensorSize == null) {
        Log.e(TAG, "Failed to get the sensor size.");
        return;
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to get the sensor size.", e);
      return;
    }

    float minZoom = getMinZoomLevel();
    float maxZoom = getMaxZoomLevel();

    if (this.mZoomLevel < minZoom) {
      Log.e(TAG, "Requested zoom level is less than minimum zoom level.");
    }
    if (this.mZoomLevel > maxZoom) {
      Log.e(TAG, "Requested zoom level is greater than maximum zoom level.");
    }
    this.mZoomLevel = Math.max(minZoom, Math.min(maxZoom, this.mZoomLevel));

    float zoomScaleFactor =
        (maxZoom == minZoom) ? minZoom : (this.mZoomLevel - minZoom) / (maxZoom - minZoom);
    int minWidth = Math.round(sensorSize.width() / maxZoom);
    int minHeight = Math.round(sensorSize.height() / maxZoom);
    int diffWidth = sensorSize.width() - minWidth;
    int diffHeight = sensorSize.height() - minHeight;
    float cropWidth = diffWidth * zoomScaleFactor;
    float cropHeight = diffHeight * zoomScaleFactor;

    Rect cropRegion =
        new Rect(
            /*left=*/ (int) Math.ceil(cropWidth / 2 - 0.5f),
            /*top=*/ (int) Math.ceil(cropHeight / 2 - 0.5f),
            /*right=*/ (int) Math.floor(sensorSize.width() - cropWidth / 2 + 0.5f),
            /*bottom=*/ (int) Math.floor(sensorSize.height() - cropHeight / 2 + 0.5f));

    if (cropRegion.width() < 50 || cropRegion.height() < 50) {
      Log.e(TAG, "Crop region is too small to compute 3A stats, so ignoring further zoom.");
      return;
    }
    this.mCropRegion = cropRegion;

    mPreview.zoom(cropRegion);
  }

  public float getMinZoomLevel() {
    return UNITY_ZOOM_SCALE;
  }

  public float getMaxZoomLevel() {
    try {
      CameraCharacteristics characteristics =
          mCameraManager.getCameraCharacteristics(getActiveCamera());
      Float maxZoom =
          characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
      if (maxZoom == null) {
        return ZOOM_NOT_SUPPORTED;
      }
      if (maxZoom == ZOOM_NOT_SUPPORTED) {
        return ZOOM_NOT_SUPPORTED;
      }
      return maxZoom;
    } catch (Exception e) {
      Log.e(TAG, "Failed to get SCALER_AVAILABLE_MAX_DIGITAL_ZOOM.", e);
    }
    return ZOOM_NOT_SUPPORTED;
  }

  public boolean isZoomSupported() {
    return getMaxZoomLevel() != ZOOM_NOT_SUPPORTED;
  }

  // TODO(b/124269166): Rethink how we can handle permissions here.
  @SuppressLint("MissingPermission")
  private void rebindToLifecycle() {
    if (mCurrentLifecycle != null) {
      bindToLifecycle(mCurrentLifecycle);
    }
  }

  int getRelativeCameraOrientation(boolean compensateForMirroring) {
    int rotationDegrees;
    try {
      String cameraId = CameraX.getCameraWithLensFacing(getLensFacing());
      CameraInfo cameraInfo = CameraX.getCameraInfo(cameraId);
      rotationDegrees = cameraInfo.getSensorRotationDegrees(getDisplaySurfaceRotation());
      if (compensateForMirroring) {
        rotationDegrees = (360 - rotationDegrees) % 360;
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to query camera", e);
      rotationDegrees = 0;
    }

    return rotationDegrees;
  }

  public void invalidateView() {
    transformPreview();
    updateViewInfo();
  }

  void clearCurrentLifecycle() {
    if (mCurrentLifecycle != null) {
      // Remove previous use cases
      CameraX.unbind(mImageCapture, mVideoCapture, mPreview);
    }

    mCurrentLifecycle = null;
  }

  private Rect getSensorSize(String cameraId) throws CameraAccessException {
    CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
    return characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
  }

  String getActiveCamera() throws CameraInfoUnavailableException {
    return CameraX.getCameraWithLensFacing(mCameraLensFacing);
  }

  @UiThread
  private void transformPreview() {
    int previewWidth = getPreviewWidth();
    int previewHeight = getPreviewHeight();
    int displayOrientation = getDisplayRotationDegrees();

    Matrix matrix = new Matrix();

    // Apply rotation of the display
    int rotation = -displayOrientation;

    int px = (int) Math.round(previewWidth / 2d);
    int py = (int) Math.round(previewHeight / 2d);

    matrix.postRotate(rotation, px, py);

    if (displayOrientation == 90 || displayOrientation == 270) {
      // Swap width and height
      float xScale = previewWidth / (float) previewHeight;
      float yScale = previewHeight / (float) previewWidth;

      matrix.postScale(xScale, yScale, px, py);
    }

    setTransform(matrix);
  }

  // Update view related information used in use cases
  private void updateViewInfo() {
    if (mImageCapture != null) {
      mImageCapture.setTargetAspectRatio(new Rational(getWidth(), getHeight()));
      mImageCapture.setTargetRotation(getDisplaySurfaceRotation());
    }

    if (mVideoCapture != null) {
      mVideoCapture.setTargetRotation(getDisplaySurfaceRotation());
    }
  }

  @RequiresPermission(permission.CAMERA)
  private Set<LensFacing> getAvailableCameraLensFacing() {
    // Start with all camera directions
    Set<LensFacing> available = new LinkedHashSet<>(Arrays.asList(LensFacing.values()));

    // If we're bound to a lifecycle, remove unavailable cameras
    if (mCurrentLifecycle != null) {
      if (!hasCameraWithLensFacing(LensFacing.BACK)) {
        available.remove(LensFacing.BACK);
      }

      if (!hasCameraWithLensFacing(LensFacing.FRONT)) {
        available.remove(LensFacing.FRONT);
      }
    }

    return available;
  }

  public FlashMode getFlash() {
    return mFlash;
  }

  public void setFlash(FlashMode flash) {
    this.mFlash = flash;

    if (mImageCapture == null) {
      // Do nothing if there is no imageCapture
      return;
    }

    mImageCapture.setFlashMode(flash);
  }

  public void enableTorch(boolean torch) {
    if (mPreview == null) {
      return;
    }
    mPreview.enableTorch(torch);
  }

  public boolean isTorchOn() {
    if (mPreview == null) {
      return false;
    }
    return mPreview.isTorchOn();
  }

  public Context getContext() {
    return mCameraXView.getContext();
  }

  public int getWidth() {
    return mCameraXView.getWidth();
  }

  public int getHeight() {
    return mCameraXView.getHeight();
  }

  public int getDisplayRotationDegrees() {
    return CameraOrientationUtil.surfaceRotationToDegrees(getDisplaySurfaceRotation());
  }

  protected int getDisplaySurfaceRotation() {
    return mCameraXView.getDisplaySurfaceRotation();
  }

  public void setSurfaceTexture(SurfaceTexture st) {
    mCameraXView.setSurfaceTexture(st);
  }

  private int getPreviewWidth() {
    return mCameraXView.getPreviewWidth();
  }

  private int getPreviewHeight() {
    return mCameraXView.getPreviewHeight();
  }

  private int getMeasuredWidth() {
    return mCameraXView.getMeasuredWidth();
  }

  private int getMeasuredHeight() {
    return mCameraXView.getMeasuredHeight();
  }

  void setTransform(final Matrix matrix) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mCameraXView.post(
          new Runnable() {
            @Override
            public void run() {
              setTransform(matrix);
            }
          });
    } else {
      mCameraXView.setTransform(matrix);
    }
  }

  /**
   * Notify the view that the source dimensions have changed.
   *
   * <p>This will allow the view to layout the preview to display the correct aspect ratio.
   *
   * @param width  width of camera source buffers.
   * @param height height of camera source buffers.
   */
  void onPreviewSourceDimensUpdated(int width, int height) {
    mCameraXView.onPreviewSourceDimensUpdated(width, height);
  }

  public CaptureMode getCaptureMode() {
    return mCaptureMode;
  }

  public void setCaptureMode(CaptureMode captureMode) {
    this.mCaptureMode = captureMode;
    rebindToLifecycle();
  }

  public long getMaxVideoDuration() {
    return mMaxVideoDuration;
  }

  public void setMaxVideoDuration(long duration) {
    mMaxVideoDuration = duration;
  }

  public long getMaxVideoSize() {
    return mMaxVideoSize;
  }

  public void setMaxVideoSize(long size) {
    mMaxVideoSize = size;
  }

  public boolean isPaused() {
    return false;
  }
}
