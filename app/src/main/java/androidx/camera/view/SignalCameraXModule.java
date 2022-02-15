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
import android.content.res.Resources;
import android.util.Rational;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCapture.OnImageCapturedCallback;
import androidx.camera.core.ImageCapture.OnImageSavedCallback;
import androidx.camera.core.Logger;
import androidx.camera.core.Preview;
import androidx.camera.core.TorchState;
import androidx.camera.core.UseCase;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.VideoCapture.OnVideoSavedCallback;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.core.impl.LensFacingConverter;
import androidx.camera.core.impl.utils.CameraOrientationUtil;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.core.impl.utils.futures.FutureCallback;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.util.Consumer;
import androidx.core.util.Preconditions;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;

import com.google.common.util.concurrent.ListenableFuture;

import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.video.VideoUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static androidx.camera.core.ImageCapture.FLASH_MODE_OFF;

/** CameraX use case operation built on @{link androidx.camera.core}. */
@RequiresApi(21)
@SuppressLint("RestrictedApi")
final class SignalCameraXModule {
  public static final String TAG = "CameraXModule";

  private static final float UNITY_ZOOM_SCALE = 1f;
  private static final float ZOOM_NOT_SUPPORTED = UNITY_ZOOM_SCALE;
  private static final Rational ASPECT_RATIO_16_9 = new Rational(16, 9);
  private static final Rational ASPECT_RATIO_4_3 = new Rational(4, 3);
  private static final Rational ASPECT_RATIO_9_16 = new Rational(9, 16);
  private static final Rational ASPECT_RATIO_3_4 = new Rational(3, 4);

  private final Preview.Builder mPreviewBuilder;
  private final VideoCapture.Builder mVideoCaptureBuilder;
  private final ImageCapture.Builder mImageCaptureBuilder;
  private final SignalCameraView mCameraView;
  final AtomicBoolean mVideoIsRecording = new AtomicBoolean(false);
  private SignalCameraView.CaptureMode mCaptureMode = SignalCameraView.CaptureMode.IMAGE;
  private long mMaxVideoDuration = SignalCameraView.INDEFINITE_VIDEO_DURATION;
  private long mMaxVideoSize = SignalCameraView.INDEFINITE_VIDEO_SIZE;
  @ImageCapture.FlashMode
  private int mFlash = FLASH_MODE_OFF;
  @Nullable
  @SuppressWarnings("WeakerAccess") /* synthetic accessor */
      Camera mCamera;
  @Nullable
  private ImageCapture mImageCapture;
  @Nullable
  private VideoCapture mVideoCapture;
  @SuppressWarnings("WeakerAccess") /* synthetic accessor */
  @Nullable
  Preview mPreview;
  @SuppressWarnings("WeakerAccess") /* synthetic accessor */
  @Nullable
  LifecycleOwner mCurrentLifecycle;
  private final LifecycleObserver mCurrentLifecycleObserver =
      new LifecycleObserver() {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        public void onDestroy(LifecycleOwner owner) {
          if (owner == mCurrentLifecycle) {
            clearCurrentLifecycle();
          }
        }
      };
  @Nullable
  private LifecycleOwner mNewLifecycle;
  @SuppressWarnings("WeakerAccess") /* synthetic accessor */
  @Nullable
  Integer mCameraLensFacing = CameraSelector.LENS_FACING_BACK;
  @SuppressWarnings("WeakerAccess") /* synthetic accessor */
  @Nullable
  ProcessCameraProvider mCameraProvider;

  // BEGIN Custom Signal Code Block
  SignalCameraXModule(SignalCameraView view, Consumer<Throwable> errorConsumer) {
    // END Custom Signal Code Block
    mCameraView = view;

    Futures.addCallback(ProcessCameraProvider.getInstance(view.getContext()),
        new FutureCallback<ProcessCameraProvider>() {
          // TODO(b/124269166): Rethink how we can handle permissions here.
          @SuppressLint("MissingPermission")
          @Override
          public void onSuccess(@Nullable ProcessCameraProvider provider) {
            Preconditions.checkNotNull(provider);
            mCameraProvider = provider;
            if (mCurrentLifecycle != null) {
              bindToLifecycle(mCurrentLifecycle);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            // BEGIN Custom Signal Code Block
            errorConsumer.accept(t);
            // END Custom Signal Code Block
          }
        }, CameraXExecutors.mainThreadExecutor());

    mPreviewBuilder = new Preview.Builder().setTargetName("Preview");

    mImageCaptureBuilder = new ImageCapture.Builder().setTargetName("ImageCapture");

    mVideoCaptureBuilder = new VideoCapture.Builder().setTargetName("VideoCapture")
                                                     .setAudioBitRate(VideoUtil.AUDIO_BIT_RATE)
                                                     .setVideoFrameRate(VideoUtil.VIDEO_FRAME_RATE)
                                                     .setBitRate(VideoUtil.VIDEO_BIT_RATE);
  }

  @RequiresPermission(permission.CAMERA)
  void bindToLifecycle(LifecycleOwner lifecycleOwner) {
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
    if (mNewLifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
      // Lifecycle is already in a destroyed state. Since it may have been a valid
      // lifecycle when bound, but became destroyed while waiting for layout, treat this as
      // a no-op now that we have cleared the previous lifecycle.
      mNewLifecycle = null;
      return;
    }
    mCurrentLifecycle = mNewLifecycle;
    mNewLifecycle = null;

    if (mCameraProvider == null) {
      // try again once the camera provider is no longer null
      return;
    }

    Set<Integer> available = getAvailableCameraLensFacing();

    if (available.isEmpty()) {
      Logger.w(TAG, "Unable to bindToLifeCycle since no cameras available");
      mCameraLensFacing = null;
    }

    // Ensure the current camera exists, or default to another camera
    if (mCameraLensFacing != null && !available.contains(mCameraLensFacing)) {
      Logger.w(TAG, "Camera does not exist with direction " + mCameraLensFacing);

      // Default to the first available camera direction
      mCameraLensFacing = available.iterator().next();

      Logger.w(TAG, "Defaulting to primary camera with direction " + mCameraLensFacing);
    }

    // Do not attempt to create use cases for a null cameraLensFacing. This could occur if
    // the user explicitly sets the LensFacing to null, or if we determined there
    // were no available cameras, which should be logged in the logic above.
    if (mCameraLensFacing == null) {
      return;
    }

    // Set the preferred aspect ratio as 4:3 if it is IMAGE only mode. Set the preferred aspect
    // ratio as 16:9 if it is VIDEO or MIXED mode. Then, it will be WYSIWYG when the view finder
    // is in CENTER_INSIDE mode.

    boolean isDisplayPortrait = getDisplayRotationDegrees() == 0
        || getDisplayRotationDegrees() == 180;

    // Begin Signal Custom Code Block
    int resolution = CameraXUtil.getIdealResolution(Resources.getSystem().getDisplayMetrics().widthPixels, Resources.getSystem().getDisplayMetrics().heightPixels);
    // End Signal Custom Code Block

    Rational targetAspectRatio;
    // Begin Signal Custom Code Block
    mImageCaptureBuilder.setTargetResolution(CameraXUtil.buildResolutionForRatio(resolution, ASPECT_RATIO_16_9, isDisplayPortrait));
    targetAspectRatio = isDisplayPortrait ? ASPECT_RATIO_9_16 : ASPECT_RATIO_16_9;
    // End Signal Custom Code Block

    // Begin Signal Custom Code Block
    mImageCaptureBuilder.setCaptureMode(CameraXUtil.getOptimalCaptureMode());
    // End Signal Custom Code Block

    mImageCaptureBuilder.setTargetRotation(getDisplaySurfaceRotation());
    mImageCapture = mImageCaptureBuilder.build();

    // Begin Signal Custom Code Block
    Size size = VideoUtil.getVideoRecordingSize();
    mVideoCaptureBuilder.setTargetResolution(size);
    mVideoCaptureBuilder.setMaxResolution(size);
    // End Signal Custom Code Block

    mVideoCaptureBuilder.setTargetRotation(getDisplaySurfaceRotation());
    // Begin Signal Custom Code Block
    if (MediaConstraints.isVideoTranscodeAvailable()) {
      mVideoCapture = mVideoCaptureBuilder.build();
    }
    // End Signal Custom Code Block

    // Adjusts the preview resolution according to the view size and the target aspect ratio.
    int height = (int) (getMeasuredWidth() / targetAspectRatio.floatValue());
    mPreviewBuilder.setTargetResolution(new Size(getMeasuredWidth(), height));

    mPreview = mPreviewBuilder.build();
    mPreview.setSurfaceProvider(mCameraView.getPreviewView().getSurfaceProvider());

    CameraSelector cameraSelector =
        new CameraSelector.Builder().requireLensFacing(mCameraLensFacing).build();
    if (getCaptureMode() == SignalCameraView.CaptureMode.IMAGE) {
      mCamera = mCameraProvider.bindToLifecycle(mCurrentLifecycle, cameraSelector,
          mImageCapture,
          mPreview);
    } else if (getCaptureMode() == SignalCameraView.CaptureMode.VIDEO) {
      mCamera = mCameraProvider.bindToLifecycle(mCurrentLifecycle, cameraSelector,
          mVideoCapture,
          mPreview);
    } else {
      mCamera = mCameraProvider.bindToLifecycle(mCurrentLifecycle, cameraSelector,
          mImageCapture,
          mVideoCapture, mPreview);
    }

    setZoomRatio(UNITY_ZOOM_SCALE);
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

  public void takePicture(Executor executor, OnImageCapturedCallback callback) {
    if (mImageCapture == null) {
      return;
    }

    if (getCaptureMode() == SignalCameraView.CaptureMode.VIDEO) {
      throw new IllegalStateException("Can not take picture under VIDEO capture mode.");
    }

    if (callback == null) {
      throw new IllegalArgumentException("OnImageCapturedCallback should not be empty");
    }

    mImageCapture.takePicture(executor, callback);
  }

  public void takePicture(@NonNull ImageCapture.OutputFileOptions outputFileOptions,
                          @NonNull Executor executor, OnImageSavedCallback callback) {
    if (mImageCapture == null) {
      return;
    }

    if (getCaptureMode() == SignalCameraView.CaptureMode.VIDEO) {
      throw new IllegalStateException("Can not take picture under VIDEO capture mode.");
    }

    if (callback == null) {
      throw new IllegalArgumentException("OnImageSavedCallback should not be empty");
    }

    outputFileOptions.getMetadata().setReversedHorizontal(mCameraLensFacing != null
        && mCameraLensFacing == CameraSelector.LENS_FACING_FRONT);
    mImageCapture.takePicture(outputFileOptions, executor, callback);
  }

  public void startRecording(VideoCapture.OutputFileOptions outputFileOptions,
                             Executor executor, final OnVideoSavedCallback callback) {
    if (mVideoCapture == null) {
      return;
    }

    if (getCaptureMode() == SignalCameraView.CaptureMode.IMAGE) {
      throw new IllegalStateException("Can not record video under IMAGE capture mode.");
    }

    if (callback == null) {
      throw new IllegalArgumentException("OnVideoSavedCallback should not be empty");
    }

    mVideoIsRecording.set(true);
    mVideoCapture.startRecording(
        outputFileOptions,
        executor,
        new VideoCapture.OnVideoSavedCallback() {
          @Override
          public void onVideoSaved(
              @NonNull VideoCapture.OutputFileResults outputFileResults) {
            mVideoIsRecording.set(false);
            callback.onVideoSaved(outputFileResults);
          }

          @Override
          public void onError(
              @VideoCapture.VideoCaptureError int videoCaptureError,
              @NonNull String message,
              @Nullable Throwable cause) {
            mVideoIsRecording.set(false);
            Logger.e(TAG, message, cause);
            callback.onError(videoCaptureError, message, cause);
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
  public void setCameraLensFacing(@Nullable Integer lensFacing) {
    // Setting same lens facing is a no-op, so check for that first
    if (!Objects.equals(mCameraLensFacing, lensFacing)) {
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
  public boolean hasCameraWithLensFacing(@CameraSelector.LensFacing int lensFacing) {
    if (mCameraProvider == null) {
      return false;
    }
    try {
      return mCameraProvider.hasCamera(
          new CameraSelector.Builder().requireLensFacing(lensFacing).build());
    } catch (CameraInfoUnavailableException e) {
      return false;
    }
  }

  @Nullable
  public Integer getLensFacing() {
    return mCameraLensFacing;
  }

  public void toggleCamera() {
    // TODO(b/124269166): Rethink how we can handle permissions here.
    @SuppressLint("MissingPermission")
    Set<Integer> availableCameraLensFacing = getAvailableCameraLensFacing();

    if (availableCameraLensFacing.isEmpty()) {
      return;
    }

    if (mCameraLensFacing == null) {
      setCameraLensFacing(availableCameraLensFacing.iterator().next());
      return;
    }

    if (mCameraLensFacing == CameraSelector.LENS_FACING_BACK
        && availableCameraLensFacing.contains(CameraSelector.LENS_FACING_FRONT)) {
      setCameraLensFacing(CameraSelector.LENS_FACING_FRONT);
      return;
    }

    if (mCameraLensFacing == CameraSelector.LENS_FACING_FRONT
        && availableCameraLensFacing.contains(CameraSelector.LENS_FACING_BACK)) {
      setCameraLensFacing(CameraSelector.LENS_FACING_BACK);
      return;
    }
  }

  public float getZoomRatio() {
    if (mCamera != null) {
      return mCamera.getCameraInfo().getZoomState().getValue().getZoomRatio();
    } else {
      return UNITY_ZOOM_SCALE;
    }
  }

  public void setZoomRatio(float zoomRatio) {
    if (mCamera != null) {
      ListenableFuture<Void> future = mCamera.getCameraControl().setZoomRatio(
          zoomRatio);
      Futures.addCallback(future, new FutureCallback<Void>() {
        @Override
        public void onSuccess(@Nullable Void result) {
        }

        @Override
        public void onFailure(Throwable t) {
          // Throw the unexpected error.
          throw new RuntimeException(t);
        }
      }, CameraXExecutors.directExecutor());
    } else {
      Logger.e(TAG, "Failed to set zoom ratio");
    }
  }

  public float getMinZoomRatio() {
    if (mCamera != null) {
      return mCamera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
    } else {
      return UNITY_ZOOM_SCALE;
    }
  }

  public float getMaxZoomRatio() {
    if (mCamera != null) {
      return mCamera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
    } else {
      return ZOOM_NOT_SUPPORTED;
    }
  }

  public boolean isZoomSupported() {
    return getMaxZoomRatio() != ZOOM_NOT_SUPPORTED;
  }

  // TODO(b/124269166): Rethink how we can handle permissions here.
  @SuppressLint("MissingPermission")
  private void rebindToLifecycle() {
    if (mCurrentLifecycle != null) {
      bindToLifecycle(mCurrentLifecycle);
    }
  }

  boolean isBoundToLifecycle() {
    return mCamera != null;
  }

  int getRelativeCameraOrientation(boolean compensateForMirroring) {
    int rotationDegrees = 0;
    if (mCamera != null) {
      rotationDegrees =
          mCamera.getCameraInfo().getSensorRotationDegrees(getDisplaySurfaceRotation());
      if (compensateForMirroring) {
        rotationDegrees = (360 - rotationDegrees) % 360;
      }
    }

    return rotationDegrees;
  }

  @SuppressLint("UnsafeExperimentalUsageError")
  public void invalidateView() {
    if (mPreview != null) {
      mPreview.setTargetRotation(getDisplaySurfaceRotation()); // Fixes issue #10940 (rotation not updated on phones using "Legacy API")
    }

    updateViewInfo();
  }

  void clearCurrentLifecycle() {
    if (mCurrentLifecycle != null && mCameraProvider != null) {
      // Remove previous use cases
      List<UseCase> toUnbind = new ArrayList<>();
      if (mImageCapture != null && mCameraProvider.isBound(mImageCapture)) {
        toUnbind.add(mImageCapture);
      }
      if (mVideoCapture != null && mCameraProvider.isBound(mVideoCapture)) {
        toUnbind.add(mVideoCapture);
      }
      if (mPreview != null && mCameraProvider.isBound(mPreview)) {
        toUnbind.add(mPreview);
      }

      if (!toUnbind.isEmpty()) {
        mCameraProvider.unbind(toUnbind.toArray((new UseCase[0])));
      }

      // Remove surface provider once unbound.
      if (mPreview != null) {
        mPreview.setSurfaceProvider(null);
      }
    }
    mCamera = null;
    mCurrentLifecycle = null;
  }

  // Update view related information used in use cases
  private void updateViewInfo() {
    if (mImageCapture != null) {
      mImageCapture.setCropAspectRatio(new Rational(getWidth(), getHeight()));
      mImageCapture.setTargetRotation(getDisplaySurfaceRotation());
    }

    if (mVideoCapture != null) {
      mVideoCapture.setTargetRotation(getDisplaySurfaceRotation());
    }
  }

  @RequiresPermission(permission.CAMERA)
  private Set<Integer> getAvailableCameraLensFacing() {
    // Start with all camera directions
    Set<Integer> available = new LinkedHashSet<>(Arrays.asList(LensFacingConverter.values()));

    // If we're bound to a lifecycle, remove unavailable cameras
    if (mCurrentLifecycle != null) {
      if (!hasCameraWithLensFacing(CameraSelector.LENS_FACING_BACK)) {
        available.remove(CameraSelector.LENS_FACING_BACK);
      }

      if (!hasCameraWithLensFacing(CameraSelector.LENS_FACING_FRONT)) {
        available.remove(CameraSelector.LENS_FACING_FRONT);
      }
    }

    return available;
  }

  @ImageCapture.FlashMode
  public int getFlash() {
    return mFlash;
  }

  // Begin Signal Custom Code Block
  public boolean hasFlash() {
    if (mImageCapture == null) {
      return false;
    }

    CameraInternal camera = mImageCapture.getCamera();

    if (camera == null) {
      return false;
    }

    return camera.getCameraInfoInternal().hasFlashUnit();
  }
  // End Signal Custom Code Block

  public void setFlash(@ImageCapture.FlashMode int flash) {
    this.mFlash = flash;

    if (mImageCapture == null) {
      // Do nothing if there is no imageCapture
      return;
    }

    mImageCapture.setFlashMode(flash);
  }

  public void enableTorch(boolean torch) {
    if (mCamera == null) {
      return;
    }
    ListenableFuture<Void> future = mCamera.getCameraControl().enableTorch(torch);
    Futures.addCallback(future, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@Nullable Void result) {
      }

      @Override
      public void onFailure(Throwable t) {
        // Throw the unexpected error.
        throw new RuntimeException(t);
      }
    }, CameraXExecutors.directExecutor());
  }

  public boolean isTorchOn() {
    if (mCamera == null) {
      return false;
    }
    return mCamera.getCameraInfo().getTorchState().getValue() == TorchState.ON;
  }

  public Context getContext() {
    return mCameraView.getContext();
  }

  public int getWidth() {
    return mCameraView.getWidth();
  }

  public int getHeight() {
    return mCameraView.getHeight();
  }

  public int getDisplayRotationDegrees() {
    return CameraOrientationUtil.surfaceRotationToDegrees(getDisplaySurfaceRotation());
  }

  protected int getDisplaySurfaceRotation() {
    return mCameraView.getDisplaySurfaceRotation();
  }

  private int getMeasuredWidth() {
    return mCameraView.getMeasuredWidth();
  }

  private int getMeasuredHeight() {
    return mCameraView.getMeasuredHeight();
  }

  @Nullable
  public Camera getCamera() {
    return mCamera;
  }

  @NonNull
  public SignalCameraView.CaptureMode getCaptureMode() {
    return mCaptureMode;
  }

  public void setCaptureMode(@NonNull SignalCameraView.CaptureMode captureMode) {
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
