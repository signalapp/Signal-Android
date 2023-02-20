package pigeon.extensions

import android.view.View
import android.widget.TextView
import org.thoughtcrime.securesms.BuildConfig

fun TextView.onFocusTextChangeListener() {
  val focus = View.OnFocusChangeListener { _, hasFocus ->
    val textSize = if (hasFocus) {
      36f
    } else {
      24f
    }
    this.textSize = textSize
  }
  this.onFocusChangeListener = focus
}

fun isSignalVersion():Boolean = BuildConfig.IS_SIGNAL