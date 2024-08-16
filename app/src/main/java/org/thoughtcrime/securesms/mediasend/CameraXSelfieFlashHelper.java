package org.thoughtcrime.securesms.mediasend;

import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.view.CameraController;

final class CameraXSelfieFlashHelper {

  private static final float MAX_SCREEN_BRIGHTNESS  = 1f;
  private static final float MAX_SELFIE_FLASH_ALPHA = 0.9f;

  private final Window            window;
  private final CameraController camera;
  private final View              selfieFlash;

  private float   brightnessBeforeFlash;
  private boolean inFlash;
  private int     flashMode = -1;

  CameraXSelfieFlashHelper(@NonNull Window window,
                           @NonNull CameraController camera,
                           @NonNull View selfieFlash)
  {
    this.window      = window;
    this.camera      = camera;
    this.selfieFlash = selfieFlash;
  }

  void onWillTakePicture() {
    if (!inFlash && shouldUseViewBasedFlash()) {
      flashMode = camera.getImageCaptureFlashMode();
      camera.setImageCaptureFlashMode(ImageCapture.FLASH_MODE_OFF);
    }
  }

  void startFlash() {
    if (inFlash || !shouldUseViewBasedFlash()) return;
    inFlash = true;

    WindowManager.LayoutParams params = window.getAttributes();

    brightnessBeforeFlash   = params.screenBrightness;
    params.screenBrightness = MAX_SCREEN_BRIGHTNESS;
    window.setAttributes(params);

    selfieFlash.setAlpha(MAX_SELFIE_FLASH_ALPHA);
  }

  void endFlash() {
    if (!inFlash) return;

    WindowManager.LayoutParams params = window.getAttributes();

    params.screenBrightness = brightnessBeforeFlash;
    window.setAttributes(params);

    camera.setImageCaptureFlashMode(flashMode);
    flashMode = -1;

    selfieFlash.setAlpha(MAX_SELFIE_FLASH_ALPHA);

    inFlash = false;
  }

  private boolean shouldUseViewBasedFlash() {
    CameraSelector cameraSelector = camera.getCameraSelector();

    return (camera.getImageCaptureFlashMode() == ImageCapture.FLASH_MODE_ON || flashMode == ImageCapture.FLASH_MODE_ON) &&
           cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA;
  }
}
