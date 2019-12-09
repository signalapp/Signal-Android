package org.thoughtcrime.securesms.mediasend;

import android.Manifest;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.util.Executors;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ValueAnimator;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXView;
import org.thoughtcrime.securesms.mediasend.camerax.VideoCapture;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.MemoryFileDescriptor;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.video.VideoUtil;

import java.io.FileDescriptor;
import java.io.IOException;

@RequiresApi(26)
class CameraXVideoCaptureHelper implements CameraButtonView.VideoCaptureListener {

  private static final String TAG               = CameraXVideoCaptureHelper.class.getName();
  private static final String VIDEO_DEBUG_LABEL = "video-capture";
  private static final long   VIDEO_SIZE        = 10 * 1024 * 1024;

  private final @NonNull Fragment             fragment;
  private final @NonNull CameraXView          camera;
  private final @NonNull Callback             callback;
  private final @NonNull MemoryFileDescriptor memoryFileDescriptor;
  private final @NonNull ValueAnimator        updateProgressAnimator;

  private       boolean       isRecording;
  private       ValueAnimator cameraMetricsAnimator;

  private final VideoCapture.OnVideoSavedListener videoSavedListener = new VideoCapture.OnVideoSavedListener() {
    @Override
    public void onVideoSaved(@NonNull FileDescriptor fileDescriptor) {
      try {
        isRecording = false;
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
      isRecording = false;
      callback.onVideoError(cause);
      Util.runOnMain(() -> resetCameraSizing());
    }
  };

  CameraXVideoCaptureHelper(@NonNull Fragment fragment,
                            @NonNull CameraButtonView captureButton,
                            @NonNull CameraXView camera,
                            @NonNull MemoryFileDescriptor memoryFileDescriptor,
                            int      maxVideoDurationSec,
                            @NonNull Callback callback)
  {
    this.fragment               = fragment;
    this.camera                 = camera;
    this.memoryFileDescriptor   = memoryFileDescriptor;
    this.callback               = callback;
    this.updateProgressAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(maxVideoDurationSec * 1000);

    updateProgressAnimator.setInterpolator(new LinearInterpolator());
    updateProgressAnimator.addUpdateListener(anim -> captureButton.setProgress(anim.getAnimatedFraction()));
    updateProgressAnimator.addListener(new AnimationEndCallback() {
      @Override
      public void onAnimationEnd(Animator animation) {
        if (isRecording) onVideoCaptureComplete();
      }
    });
  }

  @Override
  public void onVideoCaptureStarted() {
    Log.d(TAG, "onVideoCaptureStarted");

    if (canRecordAudio()) {
      isRecording = true;
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

  private void beginCameraRecording() {
    this.camera.setZoomLevel(0f);
    callback.onVideoRecordStarted();
    shrinkCaptureArea();
    camera.startRecording(memoryFileDescriptor.getFileDescriptor(), Executors.mainThreadExecutor(), videoSavedListener);
    updateProgressAnimator.start();
  }

  private void shrinkCaptureArea() {
    Size  screenSize               = getScreenSize();
    Size  videoRecordingSize       = VideoUtil.getVideoRecordingSize();
    float scale                    = getSurfaceScaleForRecording();
    float targetWidthForAnimation  = videoRecordingSize.getWidth() * scale;
    float scaleX                   = targetWidthForAnimation / screenSize.getWidth();

    if (scaleX == 1f) {
      float targetHeightForAnimation = videoRecordingSize.getHeight() * scale;
      cameraMetricsAnimator = ValueAnimator.ofFloat(screenSize.getHeight(), targetHeightForAnimation);
    } else {
      cameraMetricsAnimator = ValueAnimator.ofFloat(screenSize.getWidth(), targetWidthForAnimation);
    }

    ViewGroup.LayoutParams params = camera.getLayoutParams();
    cameraMetricsAnimator.setInterpolator(new LinearInterpolator());
    cameraMetricsAnimator.setDuration(200);
    cameraMetricsAnimator.addListener(new AnimationEndCallback() {
      @Override
      public void onAnimationEnd(Animator animation) {
        if (!isRecording) return;

        scaleCameraViewToMatchRecordingSizeAndAspectRatio();
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

  private void resetCameraSizing() {
    ViewGroup.LayoutParams layoutParams = camera.getLayoutParams();
    layoutParams.width  = ViewGroup.LayoutParams.MATCH_PARENT;
    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;

    camera.setLayoutParams(layoutParams);
    camera.setScaleX(1);
    camera.setScaleY(1);
  }

  @Override
  public void onVideoCaptureComplete() {
    isRecording = false;
    if (!canRecordAudio()) return;

    Log.d(TAG, "onVideoCaptureComplete");
    camera.stopRecording();

    if (cameraMetricsAnimator != null && cameraMetricsAnimator.isRunning()) {
      cameraMetricsAnimator.reverse();
    }

    updateProgressAnimator.cancel();
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

  private abstract class AnimationEndCallback implements Animator.AnimatorListener {

    @Override
    public final void onAnimationStart(Animator animation) {

    }

    @Override
    public final void onAnimationCancel(Animator animation) {

    }

    @Override
    public final void onAnimationRepeat(Animator animation) {

    }
  }

  interface Callback {
    void onVideoRecordStarted();
    void onVideoSaved(@NonNull FileDescriptor fd);
    void onVideoError(@Nullable Throwable cause);
  }
}
