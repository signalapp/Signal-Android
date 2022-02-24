package org.thoughtcrime.securesms.mediasend.v2.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import org.thoughtcrime.securesms.util.next

typealias OnTextAlignmentChanged = (TextAlignment) -> Unit

/**
 * Allows the user to toggle between START / END / CENTER alignment for text in a story post.
 */
class TextAlignmentButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

  private var textAlignment: TextAlignment = TextAlignment.CENTER

  var onAlignmentChangedListener: OnTextAlignmentChanged? = null

  init {
    setImageResource(textAlignment.icon)
    super.setOnClickListener {
      setAlignment(textAlignment.next())
    }
  }

  override fun setOnClickListener(l: OnClickListener?) {
    throw UnsupportedOperationException()
  }

  fun setAlignment(textAlignment: TextAlignment) {
    if (textAlignment != this.textAlignment) {
      this.textAlignment = textAlignment
      setImageResource(textAlignment.icon)
      onAlignmentChangedListener?.invoke(textAlignment)
    }
  }
}
