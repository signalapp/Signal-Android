package org.thoughtcrime.securesms.mediasend;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraX;
import androidx.fragment.app.Fragment;

public interface CameraFragment {

  @SuppressLint("RestrictedApi")
  static Fragment newInstance() {
    if (Build.VERSION.SDK_INT >= 21 && CameraX.isInitialized()) {
      return CameraXFragment.newInstance();
    } else {
      return Camera1Fragment.newInstance();
    }
  }

  interface Controller {
    void onCameraError();
    void onImageCaptured(@NonNull byte[] data, int width, int height);
    int getDisplayRotation();
  }
}
