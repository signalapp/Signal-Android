package org.thoughtcrime.securesms.mediasend.v2.text

import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.DimensionUnit
import org.signal.core.util.EditTextUtil
import org.thoughtcrime.securesms.util.doOnEachLayout

class TextStoryTextWatcher private constructor(private val textView: TextView) : TextWatcher {

  override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
    ensureProperTextSize(textView)
  }

  override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

  override fun afterTextChanged(s: Editable) = Unit

  companion object {
    fun ensureProperTextSize(textView: TextView) {
      val breakIteratorCompat = BreakIteratorCompat.getInstance()
      breakIteratorCompat.setText(textView.text)
      val length = breakIteratorCompat.countBreaks()
      val expectedTextSize = when {
        length < 50 -> 34f
        length < 200 -> 24f
        else -> 18f
      }

      if (expectedTextSize < 24f) {
        textView.gravity = Gravity.START
      } else {
        textView.gravity = Gravity.CENTER
      }

      textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, DimensionUnit.DP.toPixels(expectedTextSize))

      if (textView !is EditText) {
        textView.requestLayout()
      }
    }

    fun install(textView: TextView) {
      val watcher = TextStoryTextWatcher(textView)

      if (textView is EditText) {
        EditTextUtil.addGraphemeClusterLimitFilter(textView, 700)
      } else {
        textView.doOnEachLayout {
          val contentHeight = textView.height - textView.paddingTop - textView.paddingBottom
          if (textView.layout != null && textView.layout.height > contentHeight) {
            val percentShown = contentHeight / textView.layout.height.toFloat()
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, DimensionUnit.DP.toPixels(18f * percentShown))
          }
        }
      }

      textView.addTextChangedListener(watcher)
    }
  }
}
