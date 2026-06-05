package org.thoughtcrime.securesms.mediasend.v2

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
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

  fun setChatColor(@ColorInt color: Int) {
    ViewCompat.setBackgroundTintList(countView, ColorStateList.valueOf(color))
  }
}
