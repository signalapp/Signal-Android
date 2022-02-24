package org.thoughtcrime.securesms.mediasend.v2.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import org.thoughtcrime.securesms.util.next

typealias OnTextColorStyleChanged = (TextColorStyle) -> Unit

/**
 * Allows the user to cycle between text and background styling for a text post
 */
class TextColorStyleButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

  private var textColorStyle: TextColorStyle = TextColorStyle.NO_BACKGROUND

  var onTextColorStyleChanged: OnTextColorStyleChanged? = null

  init {
    setImageResource(textColorStyle.icon)
    super.setOnClickListener {
      setTextColorStyle(textColorStyle.next())
    }
  }

  override fun setOnClickListener(l: OnClickListener?) {
    throw UnsupportedOperationException()
  }

  fun setTextColorStyle(textColorStyle: TextColorStyle) {
    if (textColorStyle != this.textColorStyle) {
      this.textColorStyle = textColorStyle
      setImageResource(textColorStyle.icon)
      onTextColorStyleChanged?.invoke(textColorStyle)
    }
  }
}
