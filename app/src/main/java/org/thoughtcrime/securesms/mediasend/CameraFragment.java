package org.thoughtcrime.securesms.mediasend;

import android.annotation.SuppressLint;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;

import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.FileDescriptor;

public interface CameraFragment {

  float PORTRAIT_ASPECT_RATIO = 9 / 16f;

  @SuppressLint("RestrictedApi")
  static Fragment newInstance() {
    if (CameraXUtil.isSupported()) {
      return CameraXFragment.newInstance();
    } else {
      return Camera1Fragment.newInstance();
    }
  }

  @SuppressLint("RestrictedApi")
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

  void presentHud(int selectedMediaCount);
  void fadeOutControls(@NonNull Runnable onEndAction);
  void fadeInControls();

  interface Controller {
    void onCameraError();
    void onImageCaptured(@NonNull byte[] data, int width, int height);
    void onVideoCaptured(@NonNull FileDescriptor fd);
    void onVideoCaptureError();
    void onGalleryClicked();
    int getDisplayRotation();
    void onCameraCountButtonClicked();
    @NonNull LiveData<Optional<Media>> getMostRecentMediaItem();
    @NonNull MediaConstraints getMediaConstraints();
  }
}
