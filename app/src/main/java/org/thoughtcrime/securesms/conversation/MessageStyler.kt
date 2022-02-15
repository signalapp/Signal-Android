package org.thoughtcrime.securesms.conversation

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.util.PlaceholderURLSpan

/**
 * Helper for applying style-based [BodyRangeList.BodyRange]s to text.
 */
object MessageStyler {

  @JvmStatic
  fun style(messageRanges: BodyRangeList, span: SpannableString): Result {
    var hasLinks = false
    var bottomButton: BodyRangeList.BodyRange.Button? = null

    for (range in messageRanges.rangesList) {
      if (range.hasStyle()) {
        val style = range.style?.let {
          when (it) {
            BodyRangeList.BodyRange.Style.BOLD -> Typeface.BOLD
            BodyRangeList.BodyRange.Style.ITALIC -> Typeface.ITALIC
            BodyRangeList.BodyRange.Style.UNRECOGNIZED -> Typeface.NORMAL
          }
        }

        if (style != null && style != Typeface.NORMAL) {
          span.setSpan(StyleSpan(style), range.start, range.start + range.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
      } else if (range.hasLink() && range.link != null) {
        span.setSpan(PlaceholderURLSpan(range.link), range.start, range.start + range.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        hasLinks = true
      } else if (range.hasButton() && range.button != null) {
        bottomButton = range.button
      }
    }

    return Result(hasLinks, bottomButton)
  }

  data class Result(val hasStyleLinks: Boolean = false, val bottomButton: BodyRangeList.BodyRange.Button? = null) {
    companion object {
      @JvmStatic
      val NO_STYLE = Result()

      @JvmStatic
      fun none(): Result = NO_STYLE
    }
  }
}
