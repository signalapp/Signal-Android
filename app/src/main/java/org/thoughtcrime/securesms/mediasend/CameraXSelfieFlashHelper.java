package org.thoughtcrime.securesms.mediasend;

import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraX;
import androidx.camera.core.FlashMode;

import org.thoughtcrime.securesms.mediasend.camerax.CameraXView;

@RequiresApi(21)
final class CameraXSelfieFlashHelper {

  private static final float MAX_SCREEN_BRIGHTNESS    = 1f;
  private static final float MAX_SELFIE_FLASH_ALPHA   = 0.75f;
  private static final long  SELFIE_FLASH_DURATION_MS = 250;

  private final Window      window;
  private final CameraXView camera;
  private final View        selfieFlash;

  private float   brightnessBeforeFlash;
  private boolean inFlash;

  CameraXSelfieFlashHelper(@NonNull Window window,
                           @NonNull CameraXView camera,
                           @NonNull View selfieFlash)
  {
    this.window      = window;
    this.camera      = camera;
    this.selfieFlash = selfieFlash;
  }

  void startFlash() {
    if (inFlash || !shouldUseViewBasedFlash()) return;
    inFlash = true;

    WindowManager.LayoutParams params = window.getAttributes();

    brightnessBeforeFlash   = params.screenBrightness;
    params.screenBrightness = MAX_SCREEN_BRIGHTNESS;
    window.setAttributes(params);

    selfieFlash.animate()
               .alpha(MAX_SELFIE_FLASH_ALPHA)
               .setDuration(SELFIE_FLASH_DURATION_MS);
  }

  void endFlash() {
    if (!inFlash) return;

    WindowManager.LayoutParams params = window.getAttributes();

    params.screenBrightness = brightnessBeforeFlash;
    window.setAttributes(params);

    selfieFlash.animate()
               .alpha(0f)
               .setDuration(SELFIE_FLASH_DURATION_MS);

    inFlash = false;
  }

  private boolean shouldUseViewBasedFlash() {
    return camera.getFlash() == FlashMode.ON &&
           !camera.hasFlash()                &&
           camera.getCameraLensFacing() == CameraX.LensFacing.FRONT;
  }
}
