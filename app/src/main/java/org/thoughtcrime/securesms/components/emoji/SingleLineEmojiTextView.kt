package org.thoughtcrime.securesms.components.emoji

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.libsignal.util.guava.Optional

open class SingleLineEmojiTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

  private var bufferType: BufferType? = null

  init {
    maxLines = 1
  }

  override fun setText(text: CharSequence?, type: BufferType?) {
    bufferType = type
    val candidates = if (isInEditMode) null else EmojiProvider.getCandidates(text)
    if (SignalStore.settings().isPreferSystemEmoji || candidates == null || candidates.size() == 0) {
      super.setText(Optional.fromNullable(text).or(""), BufferType.NORMAL)
    } else {
      val newContent = if (width == 0) {
        text
      } else {
        TextUtils.ellipsize(text, paint, width.toFloat(), TextUtils.TruncateAt.END, false, null)
      }

      val newCandidates = if (isInEditMode) null else EmojiProvider.getCandidates(newContent)
      val newText = if (newCandidates == null || newCandidates.size() == 0) {
        newContent
      } else {
        EmojiProvider.emojify(newCandidates, newContent, this)
      }
      super.setText(newText, type)
    }
  }

  override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
    super.onSizeChanged(width, height, oldWidth, oldHeight)
    if (width > 0 && oldWidth != width) {
      setText(text, bufferType ?: BufferType.NORMAL)
    }
  }

  override fun setMaxLines(maxLines: Int) {
    check(maxLines == 1) { "setMaxLines: $maxLines != 1" }
    super.setMaxLines(maxLines)
  }
}
