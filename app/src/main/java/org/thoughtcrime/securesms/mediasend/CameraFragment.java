package org.thoughtcrime.securesms.mediasend;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil;
import org.thoughtcrime.securesms.mms.MediaConstraints;

import java.io.FileDescriptor;
import java.util.Optional;

import io.reactivex.rxjava3.core.Flowable;

public interface CameraFragment {

  float PORTRAIT_ASPECT_RATIO = 9 / 16f;

  @SuppressLint({ "RestrictedApi", "UnsafeOptInUsageError" })
  static Fragment newInstance(boolean qrScanEnabled) {
    if (CameraXUtil.isSupported()) {
      return CameraXFragment.newInstance(qrScanEnabled);
    } else {
      return Camera1Fragment.newInstance();
    }
  }

  @SuppressLint({ "RestrictedApi", "UnsafeOptInUsageError" })
  static Fragment newInstanceForAvatarCapture() {
    if (CameraXUtil.isSupported()) {
      return CameraXFragment.newInstanceForAvatarCapture();
    } else {
      return Camera1Fragment.newInstance();
    }
  }

  static float getAspectRatioForOrientation(int orientation) {
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      return PORTRAIT_ASPECT_RATIO;
    } else {
      return 1f / PORTRAIT_ASPECT_RATIO;
    }
  }

  static void toastVideoRecordingNotAvailable(@NonNull Context context) {
    Toast.makeText(context, R.string.CameraFragment__video_recording_is_not_supported_on_your_device, Toast.LENGTH_SHORT)
         .show();
  }

  void presentHud(int selectedMediaCount);
  void fadeOutControls(@NonNull Runnable onEndAction);
  void fadeInControls();

  interface Controller {
    void onCameraError();
    void onImageCaptured(@NonNull byte[] data, int width, int height);
    void onVideoCaptured(@NonNull FileDescriptor fd);
    void onVideoCaptureError();
    void onGalleryClicked();
    void onCameraCountButtonClicked();
    void onQrCodeFound(@NonNull String data);
    @NonNull Flowable<Optional<Media>> getMostRecentMediaItem();
    @NonNull MediaConstraints getMediaConstraints();
    int getMaxVideoDuration();
  }
}
