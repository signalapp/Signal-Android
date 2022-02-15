package org.thoughtcrime.securesms.mediasend.v2

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
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
}
