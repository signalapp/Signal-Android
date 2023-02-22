package pigeon.extensions

import android.view.View
import androidx.core.view.ViewCompat


fun View.onFocusTextChangeListener() {

  if (!isSignalVersion()) {
    val BUTTON_SCALE_FOCUS = 1.3f
    val BUTTON_SCALE_NON_FOCUS = 1.0f
    val BUTTON_TRANSLATION_X_FOCUS = 12.0f
    val BUTTON_TRANSLATION_X_NON_FOCUS = 1.0f

    val focus = View.OnFocusChangeListener { _, hasFocus ->
      val scale: Float = if (hasFocus) {
        BUTTON_SCALE_FOCUS
      } else {
        BUTTON_SCALE_NON_FOCUS
      }

      val translationX: Float = if (hasFocus) {
        BUTTON_TRANSLATION_X_FOCUS
      } else {
        BUTTON_TRANSLATION_X_NON_FOCUS
      }

      ViewCompat.animate(this)
        .scaleX(scale)
        .scaleY(scale)
        .translationX(translationX)
        .start()

    }
    this.onFocusChangeListener = focus
  }
}