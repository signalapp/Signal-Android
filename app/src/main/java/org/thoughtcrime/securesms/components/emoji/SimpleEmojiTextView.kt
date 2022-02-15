package org.thoughtcrime.securesms.components.emoji

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.ThrottledDebouncer
import org.whispersystems.libsignal.util.guava.Optional

open class SimpleEmojiTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

  private var bufferType: BufferType? = null
  private val sizeChangeDebouncer: ThrottledDebouncer = ThrottledDebouncer(200)

  override fun setText(text: CharSequence?, type: BufferType?) {
    bufferType = type
    val candidates = if (isInEditMode) null else EmojiProvider.getCandidates(text)
    if (SignalStore.settings().isPreferSystemEmoji || candidates == null || candidates.size() == 0) {
      super.setText(Optional.fromNullable(text).or(""), type)
    } else {
      val startDrawableSize: Int = compoundDrawables[0]?.let { it.intrinsicWidth + compoundDrawablePadding } ?: 0
      val endDrawableSize: Int = compoundDrawables[1]?.let { it.intrinsicWidth + compoundDrawablePadding } ?: 0
      val adjustedWidth: Int = width - startDrawableSize - endDrawableSize

      val newCandidates = if (isInEditMode) null else EmojiProvider.getCandidates(text)
      val newText = if (newCandidates == null || newCandidates.size() == 0) {
        text
      } else {
        EmojiProvider.emojify(newCandidates, text, this, false)
      }

      val newContent = if (width == 0 || maxLines == -1) {
        newText
      } else {
        TextUtils.ellipsize(newText, paint, (adjustedWidth * maxLines).toFloat(), TextUtils.TruncateAt.END, false, null)
      }
      bufferType = BufferType.SPANNABLE
      super.setText(newContent, type)
    }
  }

  override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
    super.onSizeChanged(width, height, oldWidth, oldHeight)
    sizeChangeDebouncer.publish {
      if (width > 0 && oldWidth != width) {
        setText(text, bufferType ?: BufferType.SPANNABLE)
      }
    }
  }
}
