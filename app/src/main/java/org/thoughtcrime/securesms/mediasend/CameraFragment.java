package org.thoughtcrime.securesms.mediasend;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraX;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil;

import java.io.FileDescriptor;
import java.util.HashSet;
import java.util.Set;

public interface CameraFragment {

  @SuppressLint("RestrictedApi")
  static Fragment newInstance() {
    if (CameraXUtil.isSupported() && CameraX.isInitialized()) {
      return CameraXFragment.newInstance();
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
