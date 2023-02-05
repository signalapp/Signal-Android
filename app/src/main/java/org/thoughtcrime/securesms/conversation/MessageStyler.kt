package org.thoughtcrime.securesms.conversation

import android.graphics.Typeface
import android.text.Spannable
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.PlaceholderURLSpan

/**
 * Helper for parsing and applying styles. Most notably with [BodyRangeList].
 */
object MessageStyler {

  const val MONOSPACE = "monospace"

  @JvmStatic
  fun boldStyle(): CharacterStyle {
    return StyleSpan(Typeface.BOLD)
  }

  @JvmStatic
  fun italicStyle(): CharacterStyle {
    return StyleSpan(Typeface.ITALIC)
  }

  @JvmStatic
  fun strikethroughStyle(): CharacterStyle {
    return StrikethroughSpan()
  }

  @JvmStatic
  fun monoStyle(): CharacterStyle {
    return TypefaceSpan(MONOSPACE)
  }

  @JvmStatic
  fun style(messageRanges: BodyRangeList?, span: Spannable): Result {
    if (messageRanges == null) {
      return Result.none()
    }

    var appliedStyle = false
    var hasLinks = false
    var bottomButton: BodyRangeList.BodyRange.Button? = null

    messageRanges
      .rangesList
      .filter { r -> r.start >= 0 && r.start < span.length && r.start + r.length >= 0 && r.start + r.length <= span.length }
      .forEach { range ->
        if (range.hasStyle()) {
          val styleSpan: CharacterStyle? = when (range.style) {
            BodyRangeList.BodyRange.Style.BOLD -> boldStyle()
            BodyRangeList.BodyRange.Style.ITALIC -> italicStyle()
            BodyRangeList.BodyRange.Style.STRIKETHROUGH -> strikethroughStyle()
            BodyRangeList.BodyRange.Style.MONOSPACE -> monoStyle()
            else -> null
          }

          if (styleSpan != null) {
            span.setSpan(styleSpan, range.start, range.start + range.length, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            appliedStyle = true
          }
        } else if (range.hasLink() && range.link != null) {
          span.setSpan(PlaceholderURLSpan(range.link), range.start, range.start + range.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          hasLinks = true
        } else if (range.hasButton() && range.button != null) {
          bottomButton = range.button
        }
      }

    return if (appliedStyle || hasLinks || bottomButton != null) {
      Result(hasLinks, bottomButton)
    } else {
      Result.none()
    }
  }

  @JvmStatic
  fun hasStyling(text: Spanned): Boolean {
    return if (FeatureFlags.textFormatting()) {
      text.getSpans(0, text.length, CharacterStyle::class.java)
        .any { s -> isSupportedCharacterStyle(s) && text.getSpanEnd(s) - text.getSpanStart(s) > 0 }
    } else {
      false
    }
  }

  @JvmStatic
  fun getStyling(text: CharSequence?): BodyRangeList? {
    val bodyRanges = if (text is Spanned && FeatureFlags.textFormatting()) {
      text
        .getSpans(0, text.length, CharacterStyle::class.java)
        .filter { s -> isSupportedCharacterStyle(s) }
        .mapNotNull { span: CharacterStyle ->
          val spanStart = text.getSpanStart(span)
          val spanLength = text.getSpanEnd(span) - spanStart

          val style = when (span) {
            is StyleSpan -> if (span.style == Typeface.BOLD) BodyRangeList.BodyRange.Style.BOLD else BodyRangeList.BodyRange.Style.ITALIC
            is StrikethroughSpan -> BodyRangeList.BodyRange.Style.STRIKETHROUGH
            is TypefaceSpan -> BodyRangeList.BodyRange.Style.MONOSPACE
            else -> throw IllegalArgumentException("Provided text contains unsupported spans")
          }

          if (spanLength > 0) {
            BodyRangeList.BodyRange.newBuilder().setStart(spanStart).setLength(spanLength).setStyle(style).build()
          } else {
            null
          }
        }
        .toList()
    } else {
      emptyList()
    }

    return if (bodyRanges.isNotEmpty()) {
      BodyRangeList.newBuilder().addAllRanges(bodyRanges).build()
    } else {
      null
    }
  }

  private fun isSupportedCharacterStyle(style: CharacterStyle): Boolean {
    return when (style) {
      is StyleSpan -> style.style == Typeface.ITALIC || style.style == Typeface.BOLD
      is StrikethroughSpan -> true
      is TypefaceSpan -> style.family == MONOSPACE
      else -> false
    }
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
