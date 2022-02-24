package org.thoughtcrime.securesms.mediasend.v2.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import org.thoughtcrime.securesms.fonts.TextFont
import org.thoughtcrime.securesms.util.next

typealias OnTextFontChanged = (TextFont) -> Unit

/**
 * Allows the user to cycle between fonts for a story text post
 */
class TextFontButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

  private var textFont: TextFont = TextFont.REGULAR

  var onTextFontChanged: OnTextFontChanged? = null

  init {
    setImageResource(textFont.icon)
    super.setOnClickListener {
      setTextFont(textFont.next())
    }
  }

  override fun setOnClickListener(l: OnClickListener?) {
    throw UnsupportedOperationException()
  }

  fun setTextFont(textFont: TextFont) {
    if (textFont != this.textFont) {
      this.textFont = textFont
      setImageResource(textFont.icon)
      onTextFontChanged?.invoke(textFont)
    }
  }
}
