package org.thoughtcrime.securesms.mediasend;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ZoomState;
import androidx.camera.view.CameraController;
import androidx.camera.view.PreviewView;
import androidx.camera.view.video.ExperimentalVideo;
import androidx.camera.view.video.OnVideoSavedCallback;
import androidx.camera.view.video.OutputFileOptions;
import androidx.camera.view.video.OutputFileResults;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.util.Executors;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModePolicy;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.MemoryFileDescriptor;
import org.thoughtcrime.securesms.video.VideoUtil;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@RequiresApi(26)
@ExperimentalVideo
class CameraXVideoCaptureHelper implements CameraButtonView.VideoCaptureListener {

  private static final String TAG               = CameraXVideoCaptureHelper.class.getName();
  private static final String VIDEO_DEBUG_LABEL = "video-capture";
  private static final long   VIDEO_SIZE        = 10 * 1024 * 1024;

  private final @NonNull Fragment             fragment;
  private final @NonNull PreviewView          previewView;
  private final @NonNull CameraController     cameraController;
  private final @NonNull Callback             callback;
  private final @NonNull MemoryFileDescriptor memoryFileDescriptor;
  private final @NonNull ValueAnimator        updateProgressAnimator;
  private final @NonNull Debouncer            debouncer;
  private final @NonNull CameraXModePolicy    cameraXModePolicy;

  private ValueAnimator cameraMetricsAnimator;

  private final OnVideoSavedCallback videoSavedListener = new OnVideoSavedCallback() {
    @SuppressLint("RestrictedApi")
    @Override
    public void onVideoSaved(@NonNull OutputFileResults outputFileResults) {
      try {
        debouncer.clear();
        cameraController.setZoomRatio(Objects.requireNonNull(cameraController.getZoomState().getValue()).getMinZoomRatio());
        memoryFileDescriptor.seek(0);
        callback.onVideoSaved(memoryFileDescriptor.getFileDescriptor());
      } catch (IOException e) {
        callback.onVideoError(e);
      }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
      debouncer.clear();
      callback.onVideoError(cause);
    }
  };

  CameraXVideoCaptureHelper(@NonNull Fragment fragment,
                            @NonNull CameraButtonView captureButton,
                            @NonNull CameraController cameraController,
                            @NonNull PreviewView previewView,
                            @NonNull MemoryFileDescriptor memoryFileDescriptor,
                            @NonNull CameraXModePolicy cameraXModePolicy,
                            int maxVideoDurationSec,
                            @NonNull Callback callback)
  {
    this.fragment               = fragment;
    this.cameraController       = cameraController;
    this.previewView            = previewView;
    this.memoryFileDescriptor   = memoryFileDescriptor;
    this.callback               = callback;

    float animationScale = ContextUtil.getAnimationScale(fragment.requireContext());
    long  baseDuration   = TimeUnit.SECONDS.toMillis(maxVideoDurationSec);
    long  scaledDuration = Math.round(animationScale > 0f ? (baseDuration * (1f / animationScale)) : baseDuration);

    this.updateProgressAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(scaledDuration);
    this.debouncer              = new Debouncer(TimeUnit.SECONDS.toMillis(maxVideoDurationSec));
    this.cameraXModePolicy      = cameraXModePolicy;

    updateProgressAnimator.setInterpolator(new LinearInterpolator());
    updateProgressAnimator.addUpdateListener(anim -> {
      captureButton.setProgress(anim.getAnimatedFraction());
    });
  }

  @Override
  public void onVideoCaptureStarted() {
    Log.d(TAG, "onVideoCaptureStarted");

    if (canRecordAudio()) {
      beginCameraRecording();
    } else {
      displayAudioRecordingPermissionsDialog();
    }
  }

  private boolean canRecordAudio() {
    return Permissions.hasAll(fragment.requireContext(), Manifest.permission.RECORD_AUDIO);
  }

  private void displayAudioRecordingPermissionsDialog() {
    Permissions.with(fragment)
               .request(Manifest.permission.RECORD_AUDIO)
               .ifNecessary()
               .withRationaleDialog(fragment.getString(R.string.ConversationActivity_enable_the_microphone_permission_to_capture_videos_with_sound), R.drawable.ic_mic_solid_24)
               .withPermanentDenialDialog(fragment.getString(R.string.ConversationActivity_signal_needs_the_recording_permissions_to_capture_video))
               .onAnyDenied(() -> Toast.makeText(fragment.requireContext(), R.string.ConversationActivity_signal_needs_recording_permissions_to_capture_video, Toast.LENGTH_LONG).show())
               .execute();
  }

  @SuppressLint("RestrictedApi")
  private void beginCameraRecording() {
    cameraXModePolicy.setToVideo(cameraController);
    this.cameraController.setZoomRatio(Objects.requireNonNull(this.cameraController.getZoomState().getValue()).getMinZoomRatio());
    callback.onVideoRecordStarted();
    shrinkCaptureArea();

    OutputFileOptions options = OutputFileOptions.builder(memoryFileDescriptor.getParcelFileDescriptor()).build();

    cameraController.startRecording(options, Executors.mainThreadExecutor(), videoSavedListener);
    updateProgressAnimator.start();
    debouncer.publish(this::onVideoCaptureComplete);
  }

  private void shrinkCaptureArea() {
    Size  screenSize              = getScreenSize();
    Size  videoRecordingSize      = VideoUtil.getVideoRecordingSize();
    float scale                   = getSurfaceScaleForRecording();
    float targetWidthForAnimation = videoRecordingSize.getWidth() * scale;
    float scaleX                  = targetWidthForAnimation / screenSize.getWidth();

    if (scaleX == 1f) {
      float targetHeightForAnimation = videoRecordingSize.getHeight() * scale;

      if (screenSize.getHeight() == targetHeightForAnimation) {
        return;
      }

      cameraMetricsAnimator = ValueAnimator.ofFloat(screenSize.getHeight(), targetHeightForAnimation);
    } else {

      if (screenSize.getWidth() == targetWidthForAnimation) {
        return;
      }

      cameraMetricsAnimator = ValueAnimator.ofFloat(screenSize.getWidth(), targetWidthForAnimation);
    }

    ViewGroup.LayoutParams params = previewView.getLayoutParams();
    cameraMetricsAnimator.setInterpolator(new LinearInterpolator());
    cameraMetricsAnimator.setDuration(200);
    cameraMetricsAnimator.addUpdateListener(animation -> {
      if (scaleX == 1f) {
        params.height = Math.round((float) animation.getAnimatedValue());
      } else {
        params.width = Math.round((float) animation.getAnimatedValue());
      }
      previewView.setLayoutParams(params);
    });
    cameraMetricsAnimator.start();
  }

  private Size getScreenSize() {
    DisplayMetrics metrics = previewView.getResources().getDisplayMetrics();
    return new Size(metrics.widthPixels, metrics.heightPixels);
  }

  private float getSurfaceScaleForRecording() {
    Size videoRecordingSize = VideoUtil.getVideoRecordingSize();
    Size screenSize         = getScreenSize();
    return Math.min(screenSize.getHeight(), screenSize.getWidth()) / (float) Math.min(videoRecordingSize.getHeight(), videoRecordingSize.getWidth());
  }

  @Override
  public void onVideoCaptureComplete() {
    if (!canRecordAudio()) return;

    Log.d(TAG, "onVideoCaptureComplete");
    cameraController.stopRecording();

    if (cameraMetricsAnimator != null && cameraMetricsAnimator.isRunning()) {
      cameraMetricsAnimator.reverse();
    }

    updateProgressAnimator.cancel();
    debouncer.clear();
    cameraXModePolicy.setToImage(cameraController);
  }

  @Override
  public void onZoomIncremented(float increment) {
    ZoomState zoomState = Objects.requireNonNull(cameraController.getZoomState().getValue());
    float range = zoomState.getMaxZoomRatio() - zoomState.getMinZoomRatio();
    cameraController.setZoomRatio((range * increment) + zoomState.getMinZoomRatio());
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
