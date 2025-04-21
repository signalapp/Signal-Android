package org.thoughtcrime.securesms.mediapreview.caption

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiTextView

class ExpandingCaptionView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : EmojiTextView(context, attrs, defStyleAttr) {

  var expandedHeight = 0

  private var expanded: Boolean = false

  var fullCaptionText: CharSequence = ""
    set(value) {
      field = value
      expanded = false
      updateExpansionState(expanded)
    }

  init {
    movementMethod = LinkMovementMethod.getInstance()
    val overflow = SpannableString(context.getString(R.string.MediaPreviewFragment_read_more_overflow_text))
    overflow.setSpan(StyleSpan(Typeface.BOLD), 0, overflow.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    setOverflowText(overflow)
    bindGestureListener()
  }

  private fun toggleExpansion() {
    expanded = if (isExpandable()) {
      !expanded
    } else {
      false
    }
    updateExpansionState(expanded)
  }

  private fun updateExpansionState(expand: Boolean) {
    if (expand) {
      setMaxLength(-1)
      text = fullCaptionText
      updateLayoutParams { height = expandedHeight }
      post {
        scrollTo(0, 0)
      }
    } else {
      setMaxLength(CHAR_LIMIT_MESSAGE_PREVIEW)
      text = fullCaptionText
      updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    }

    if (isExpandable()) {
      setOnClickListener { toggleExpansion() }
    } else {
      setOnClickListener(null)
    }
  }

  private fun isExpandable(): Boolean {
    return fullCaptionText.length > CHAR_LIMIT_MESSAGE_PREVIEW
  }

  companion object {
    const val CHAR_LIMIT_MESSAGE_PREVIEW = 280
  }
}
