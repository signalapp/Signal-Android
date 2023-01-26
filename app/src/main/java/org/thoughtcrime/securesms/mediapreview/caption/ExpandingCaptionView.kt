package org.thoughtcrime.securesms.mediapreview.caption

import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiTextView

class ExpandingCaptionView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
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
    setOnClickListener { toggleExpansion() }
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
      text = fullCaptionText
      movementMethod = ScrollingMovementMethod()
      scrollTo(0, 0)
      updateLayoutParams { height = expandedHeight }
    } else {
      text = if (isExpandable()) {
        context.getString(R.string.MediaPreviewFragment_see_more, fullCaptionText.substring(0, CHAR_LIMIT_MESSAGE_PREVIEW))
      } else {
        fullCaptionText
      }
      movementMethod = null
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
