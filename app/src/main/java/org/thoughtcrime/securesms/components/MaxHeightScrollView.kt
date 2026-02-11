package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView
import org.thoughtcrime.securesms.R

class MaxHeightScrollView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

  private var maxHeight: Int = -1

  init {
    if (attrs != null) {
      val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView, 0, 0)
      maxHeight = typedArray.getDimensionPixelOffset(R.styleable.MaxHeightScrollView_scrollView_maxHeight, -1)
      typedArray.recycle()
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    var finalHeightMeasureSpec = heightMeasureSpec
    if (maxHeight >= 0) {
      finalHeightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
    }
    super.onMeasure(widthMeasureSpec, finalHeightMeasureSpec)
  }
}
