package org.thoughtcrime.securesms.mediasend.v2

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import org.signal.core.ui.R as CoreUiR
import org.thoughtcrime.securesms.R

class MediaCountIndicatorButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  init {
    inflate(context, R.layout.v2_media_count_indicator_button, this)
  }

  private val countView: TextView = findViewById(R.id.media_count_indicator_text)

  fun setCount(count: Int) {
    countView.text = "$count"
  }

  fun setChatColor(@ColorInt color: Int, needsDarkText: Boolean = false) {
    ViewCompat.setBackgroundTintList(countView, ColorStateList.valueOf(color))
    countView.setTextColor(
      if (needsDarkText) {
        ContextCompat.getColor(context, R.color.black)
      } else {
        ContextCompat.getColor(context, CoreUiR.color.signal_light_colorBackground)
      }
    )
  }
}
