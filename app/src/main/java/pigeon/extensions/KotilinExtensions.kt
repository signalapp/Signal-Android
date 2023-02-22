package pigeon.extensions

import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat


fun View.focusOnLeft() {

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

fun View.focusOnRight() {

  if (!isSignalVersion()) {
    val BUTTON_SCALE_FOCUS = 1.3f
    val BUTTON_SCALE_NON_FOCUS = 1.0f
    val BUTTON_TRANSLATION_X_FOCUS = 50.0f
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
        .translationX(translationX)
        .scaleX(scale)
        .scaleY(scale)
        .start()

    }
    this.onFocusChangeListener = focus
  }
}

fun TextView.setBigText() {
  this.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
}