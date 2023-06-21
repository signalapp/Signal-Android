package org.thoughtcrime.securesms.components

import android.text.Annotation
import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import org.signal.core.util.StringUtil
import org.thoughtcrime.securesms.conversation.MessageStyler
import org.thoughtcrime.securesms.conversation.MessageStyler.isSupportedStyle

/**
 * Formatting should only grow when appending until a white space character is entered/pasted.
 *
 * This watcher observes changes to the text and will shrink supported style ranges as necessary
 * to provide the desired behavior.
 */
class ComposeTextStyleWatcher : TextWatcher {
  private val markerAnnotation = Annotation("text-formatting", "marker")
  private var textSnapshotPriorToChange: CharSequence? = null

  override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
    if (s is Spannable) {
      s.removeSpan(markerAnnotation)
    }

    textSnapshotPriorToChange = s.subSequence(start, start + count)
  }

  override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    if (s is Spannable) {
      s.removeSpan(markerAnnotation)

      if (count > 0) {
        s.setSpan(markerAnnotation, start, start + count, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    }
  }

  override fun afterTextChanged(s: Editable) {
    val editStart = s.getSpanStart(markerAnnotation)
    val editEnd = s.getSpanEnd(markerAnnotation)

    s.removeSpan(markerAnnotation)

    try {
      if (editStart < 0 || editEnd < 0 || editStart >= editEnd || (editStart == 0 && editEnd == s.length)) {
        return
      }

      val change = s.subSequence(editStart, editEnd)
      if (change.isEmpty() || textSnapshotPriorToChange == null || (editEnd - editStart == 1 && !StringUtil.isVisuallyEmpty(change[0])) || TextUtils.equals(textSnapshotPriorToChange, change)) {
        textSnapshotPriorToChange = null
        return
      }
      textSnapshotPriorToChange = null

      var newEnd = editStart
      for (i in change.indices) {
        if (StringUtil.isVisuallyEmpty(change[i])) {
          newEnd = editStart + i
          break
        }
      }

      s.getSpans(editStart, editEnd, Object::class.java)
        .filter { it.isSupportedStyle() }
        .forEach { style ->
          val styleStart = s.getSpanStart(style)
          val styleEnd = s.getSpanEnd(style)

          if (styleEnd == editEnd && styleStart < styleEnd) {
            s.removeSpan(style)
            s.setSpan(style, styleStart, newEnd, MessageStyler.SPAN_FLAGS)
          } else if (styleStart >= styleEnd) {
            s.removeSpan(style)
          }
        }
    } finally {
      s.getSpans(editStart, editEnd, Object::class.java)
        .filter { it.isSupportedStyle() }
        .forEach { style ->
          val styleStart = s.getSpanStart(style)
          val styleEnd = s.getSpanEnd(style)
          if (styleEnd == styleStart || styleStart > styleEnd) {
            s.removeSpan(style)
          }
        }
    }
  }
}
