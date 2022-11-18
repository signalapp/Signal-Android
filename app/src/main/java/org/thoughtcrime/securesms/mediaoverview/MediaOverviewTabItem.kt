package org.thoughtcrime.securesms.mediaoverview

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import org.thoughtcrime.securesms.R

class MediaOverviewTabItem @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private lateinit var unselectedTextView: TextView
  private lateinit var selectedTextView: TextView

  override fun onFinishInflate() {
    super.onFinishInflate()

    unselectedTextView = findViewById(android.R.id.text1)
    selectedTextView = findViewById(R.id.text1_bold)

    unselectedTextView.doAfterTextChanged {
      selectedTextView.text = it
    }
  }

  fun select() {
    unselectedTextView.alpha = 0f
    selectedTextView.alpha = 1f
  }

  fun unselect() {
    unselectedTextView.alpha = 1f
    selectedTextView.alpha = 0f
  }
}
