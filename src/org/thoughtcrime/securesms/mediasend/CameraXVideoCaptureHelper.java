package org.thoughtcrime.securesms.mediasend;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.components.TooltipPopup;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXView;
import org.thoughtcrime.securesms.mediasend.camerax.VideoCapture;
import org.thoughtcrime.securesms.util.MemoryFileDescriptor;
import org.thoughtcrime.securesms.video.VideoUtil;

import java.io.FileDescriptor;
import java.io.IOException;

@RequiresApi(26)
class CameraXVideoCaptureHelper implements CameraButtonView.VideoCaptureListener {

  private static final String TAG               = CameraXVideoCaptureHelper.class.getName();
  private static final String VIDEO_DEBUG_LABEL = "video-capture";
  private static final long   VIDEO_SIZE        = 10 * 1024 * 1024;

  private final @NonNull CameraXView          camera;
  private final @NonNull Callback             callback;
  private final @NonNull MemoryFileDescriptor memoryFileDescriptor;

  private final ValueAnimator updateProgressAnimator = ValueAnimator.ofFloat(0f, 1f)
      .setDuration(VideoUtil.VIDEO_MAX_LENGTH_S * 1000);

  private final VideoCapture.OnVideoSavedListener videoSavedListener = new VideoCapture.OnVideoSavedListener() {
    @Override
    public void onVideoSaved(@NonNull FileDescriptor fileDescriptor) {
      try {
        camera.setZoomLevel(0f);
        memoryFileDescriptor.seek(0);
        callback.onVideoSaved(fileDescriptor);
      } catch (IOException e) {
        callback.onVideoError(e);
      }
    }

    @Override
    public void onError(@NonNull VideoCapture.VideoCaptureError videoCaptureError,
                        @NonNull String message,
                        @Nullable Throwable cause)
    {
      callback.onVideoError(cause);
    }
  };

  CameraXVideoCaptureHelper(@NonNull CameraButtonView captureButton,
                            @NonNull CameraXView camera,
                            @NonNull MemoryFileDescriptor memoryFileDescriptor,
                            @NonNull Callback callback)
  {
    this.camera               = camera;
    this.memoryFileDescriptor = memoryFileDescriptor;
    this.callback             = callback;

    updateProgressAnimator.setInterpolator(new LinearInterpolator());
    updateProgressAnimator.addUpdateListener(anim -> captureButton.setProgress(anim.getAnimatedFraction()));
    updateProgressAnimator.addListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        onVideoCaptureComplete();
      }
    });
  }

  @Override
  public void onVideoCaptureStarted() {
    Log.d(TAG, "onVideoCaptureStarted");

    this.camera.setZoomLevel(0f);
    callback.onVideoRecordStarted();
    shrinkCaptureArea(() -> {
      camera.startRecording(memoryFileDescriptor.getFileDescriptor(), videoSavedListener);
      updateProgressAnimator.start();
    });
  }

  private void shrinkCaptureArea(@NonNull Runnable onCaptureAreaShrank) {
    Size  screenSize               = getScreenSize();
    Size  videoRecordingSize       = VideoUtil.getVideoRecordingSize();
    float scale                    = getSurfaceScaleForRecording();
    float targetWidthForAnimation  = videoRecordingSize.getWidth() * scale;
    float scaleX                   = targetWidthForAnimation / screenSize.getWidth();

    final ValueAnimator cameraMetricsAnimator;
    if (scaleX == 1f) {
      float targetHeightForAnimation = videoRecordingSize.getHeight() * scale;
      cameraMetricsAnimator = ValueAnimator.ofFloat(screenSize.getHeight(), targetHeightForAnimation);
    } else {
      cameraMetricsAnimator = ValueAnimator.ofFloat(screenSize.getWidth(), targetWidthForAnimation);
    }

    ViewGroup.LayoutParams params = camera.getLayoutParams();
    cameraMetricsAnimator.setInterpolator(new LinearInterpolator());
    cameraMetricsAnimator.setDuration(200);
    cameraMetricsAnimator.addListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        scaleCameraViewToMatchRecordingSizeAndAspectRatio();
        onCaptureAreaShrank.run();
      }
    });
    cameraMetricsAnimator.addUpdateListener(animation -> {
      if (scaleX == 1f) {
        params.height = Math.round((float) animation.getAnimatedValue());
      } else {
        params.width = Math.round((float) animation.getAnimatedValue());
      }
      camera.setLayoutParams(params);
    });
    cameraMetricsAnimator.start();
  }

  private void scaleCameraViewToMatchRecordingSizeAndAspectRatio() {
    ViewGroup.LayoutParams layoutParams = camera.getLayoutParams();

    Size  videoRecordingSize = VideoUtil.getVideoRecordingSize();
    float scale              = getSurfaceScaleForRecording();

    layoutParams.height = videoRecordingSize.getHeight();
    layoutParams.width  = videoRecordingSize.getWidth();

    camera.setLayoutParams(layoutParams);
    camera.setScaleX(scale);
    camera.setScaleY(scale);
  }

  private Size getScreenSize() {
    DisplayMetrics metrics = camera.getResources().getDisplayMetrics();
    return new Size(metrics.widthPixels, metrics.heightPixels);
  }

  private float getSurfaceScaleForRecording() {
    Size videoRecordingSize = VideoUtil.getVideoRecordingSize();
    Size screenSize         = getScreenSize();
    return Math.min(screenSize.getHeight(), screenSize.getWidth()) / (float) Math.min(videoRecordingSize.getHeight(), videoRecordingSize.getWidth());
  }

  @Override
  public void onVideoCaptureComplete() {
    Log.d(TAG, "onVideoCaptureComplete");
    updateProgressAnimator.cancel();
    camera.stopRecording();
  }

  @Override
  public void onZoomIncremented(float increment) {
    float range = camera.getMaxZoomLevel() - camera.getMinZoomLevel();
    camera.setZoomLevel(range * increment);
  }

  static MemoryFileDescriptor createFileDescriptor(@NonNull Context context) throws MemoryFileDescriptor.MemoryFileException {
    return MemoryFileDescriptor.newMemoryFileDescriptor(
        context,
        VIDEO_DEBUG_LABEL,
        VIDEO_SIZE
    );
  }

  interface Callback {
    void onVideoRecordStarted();
    void onVideoSaved(@NonNull FileDescriptor fd);
    void onVideoError(@Nullable Throwable cause);
  }
}
