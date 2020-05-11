package org.thoughtcrime.securesms.mediasend;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil;

import java.io.FileDescriptor;

public interface CameraFragment {

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

  interface Controller {
    void onCameraError();
    void onImageCaptured(@NonNull byte[] data, int width, int height);
    void onVideoCaptured(@NonNull FileDescriptor fd);
    void onVideoCaptureError();
    void onGalleryClicked();
    int getDisplayRotation();
    void onCameraCountButtonClicked();
  }
}
