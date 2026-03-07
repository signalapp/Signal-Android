package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class SquareImageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    // noinspection SuspiciousNameCombination
    super.onMeasure(widthMeasureSpec, widthMeasureSpec)
  }
}
