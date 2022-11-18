package org.thoughtcrime.securesms.mediasend

import android.view.Window
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Modifies screen brightness to increase to a max of 66% if lower than that for optimal picture
 * taking conditions.
 */
class CameraScreenBrightnessController(private val window: Window) : DefaultLifecycleObserver {

  companion object {
    private const val MIN_CAMERA_BRIGHTNESS = 0.66f
  }

  private var originalBrightness: Float = 0f

  override fun onResume(owner: LifecycleOwner) {
    val originalBrightness = window.attributes.screenBrightness
    if (originalBrightness < MIN_CAMERA_BRIGHTNESS) {
      window.attributes = window.attributes.apply {
        screenBrightness = MIN_CAMERA_BRIGHTNESS
      }
    }
  }

  override fun onPause(owner: LifecycleOwner) {
    if (originalBrightness > 0f && window.attributes.screenBrightness == MIN_CAMERA_BRIGHTNESS) {
      window.attributes = window.attributes.apply {
        screenBrightness = originalBrightness
      }
    }
  }
}
