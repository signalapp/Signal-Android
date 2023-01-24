package org.thoughtcrime.securesms.mediapreview.caption

import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import org.signal.core.util.logging.Log
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
      updateExpansionState()
    }

  init {
    setOnClickListener { toggleExpansion() }
  }

  fun toggleExpansion() {
    expanded = !expanded
    updateExpansionState()
  }

  private fun updateExpansionState() {
    if (expanded) {
      Log.d(TAG, "The view should be expanded now.")
      text = fullCaptionText
      movementMethod = ScrollingMovementMethod()
      scrollTo(0, 0)
      updateLayoutParams { height = expandedHeight }
    } else {
      Log.d(TAG, "The view should be collapsed now.")
      text = if (fullCaptionText.length <= CHAR_LIMIT_MESSAGE_PREVIEW) {
        fullCaptionText
      } else {
        context.getString(R.string.MediaPreviewFragment_see_more, fullCaptionText.substring(0, CHAR_LIMIT_MESSAGE_PREVIEW))
      }
      movementMethod = null
      updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    }
    setOnClickListener { toggleExpansion() }
  }

  companion object {
    private val TAG = Log.tag(ExpandingCaptionView::class.java)
    const val CHAR_LIMIT_MESSAGE_PREVIEW = 280
  }
}
