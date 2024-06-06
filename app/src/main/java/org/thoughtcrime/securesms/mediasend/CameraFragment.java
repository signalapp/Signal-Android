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

  static float getAspectRatioForOrientation(int orientation, @NonNull CameraFragment.AspectRatioPreference preference) {
    switch (preference) {
      case NORMAL_4_3:
        return 4f / 3f;
      case NORMAL_16_9:
        return 16f / 9f;
      case PORTRAIT:  // Maintain existing portrait mode behavior
        return orientation == Configuration.ORIENTATION_PORTRAIT ? 9f / 16f : 16f / 9f;
      default:
        return 16f / 9f; // Default to 16:9 if preference is unknown
    }
  }

  enum AspectRatioPreference {
    NORMAL_4_3,
    NORMAL_16_9,
    PORTRAIT
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
    @NonNull CameraFragment.AspectRatioPreference getAspectRatioPreference();
  }
}
