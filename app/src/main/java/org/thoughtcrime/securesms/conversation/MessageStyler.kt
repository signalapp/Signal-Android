package org.thoughtcrime.securesms.conversation

import android.graphics.Typeface
import android.text.Annotation
import android.text.Spannable
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.util.PlaceholderURLSpan

/**
 * Helper for parsing and applying styles. Most notably with [BodyRangeList].
 */
object MessageStyler {

  const val MONOSPACE = "monospace"
  const val SPAN_FLAGS = Spanned.SPAN_EXCLUSIVE_INCLUSIVE
  const val DRAFT_ID = "DRAFT"
  const val COMPOSE_ID = "COMPOSE"
  const val QUOTE_ID = "QUOTE"

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
  fun spoilerStyle(id: Any, start: Int, length: Int): Annotation {
    return SpoilerAnnotation.spoilerAnnotation(arrayOf(id, start, length).contentHashCode())
  }

  @JvmStatic
  @JvmOverloads
  fun style(id: Any, messageRanges: BodyRangeList?, span: Spannable, hideSpoilerText: Boolean = true): Result {
    if (messageRanges == null) {
      return Result.none()
    }

    var appliedStyle = false
    var hasLinks = false
    var bottomButton: BodyRangeList.BodyRange.Button? = null

    messageRanges
      .rangesList
      .filter { r -> r.start >= 0 && r.start < span.length && r.start + r.length >= 0 }
      .forEach { range ->
        val start = range.start
        val end = (range.start + range.length).coerceAtMost(span.length)

        if (range.hasStyle()) {
          val styleSpan: Any? = when (range.style) {
            BodyRangeList.BodyRange.Style.BOLD -> boldStyle()
            BodyRangeList.BodyRange.Style.ITALIC -> italicStyle()
            BodyRangeList.BodyRange.Style.STRIKETHROUGH -> strikethroughStyle()
            BodyRangeList.BodyRange.Style.MONOSPACE -> monoStyle()
            BodyRangeList.BodyRange.Style.SPOILER -> {
              val spoiler = spoilerStyle(id, range.start, range.length)
              if (hideSpoilerText) {
                span.setSpan(SpoilerAnnotation.SpoilerClickableSpan(spoiler), start, end, SPAN_FLAGS)
              }
              spoiler
            }
            else -> null
          }

          if (styleSpan != null) {
            span.setSpan(styleSpan, start, end, SPAN_FLAGS)
            appliedStyle = true
          }
        } else if (range.hasLink() && range.link != null) {
          span.setSpan(PlaceholderURLSpan(range.link), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
    return text
      .getSpans(0, text.length, Object::class.java)
      .any { s -> s.isSupportedStyle() && text.getSpanEnd(s) - text.getSpanStart(s) > 0 }
  }

  @JvmStatic
  fun getStyling(text: CharSequence?): BodyRangeList? {
    val bodyRanges = if (text is Spanned) {
      text
        .getSpans(0, text.length, Object::class.java)
        .filter { s -> s.isSupportedStyle() }
        .mapNotNull { span ->
          val spanStart = text.getSpanStart(span)
          val spanLength = text.getSpanEnd(span) - spanStart

          val style: BodyRangeList.BodyRange.Style? = when (span) {
            is StyleSpan -> {
              when (span.style) {
                Typeface.BOLD -> BodyRangeList.BodyRange.Style.BOLD
                Typeface.ITALIC -> BodyRangeList.BodyRange.Style.ITALIC
                else -> null
              }
            }
            is StrikethroughSpan -> BodyRangeList.BodyRange.Style.STRIKETHROUGH
            is TypefaceSpan -> BodyRangeList.BodyRange.Style.MONOSPACE
            is Annotation -> {
              if (SpoilerAnnotation.isSpoilerAnnotation(span)) {
                BodyRangeList.BodyRange.Style.SPOILER
              } else {
                null
              }
            }
            else -> throw IllegalArgumentException("Provided text contains unsupported spans")
          }

          if (spanLength > 0 && style != null) {
            BodyRangeList.BodyRange.newBuilder().setStart(spanStart).setLength(spanLength).setStyle(style).build()
          } else {
            null
          }
        }
    } else {
      emptyList()
    }

    return if (bodyRanges.isNotEmpty()) {
      BodyRangeList.newBuilder().addAllRanges(bodyRanges).build()
    } else {
      null
    }
  }

  fun Any.isSupportedStyle(): Boolean {
    return when (this) {
      is CharacterStyle -> isSupportedCharacterStyle(this)
      is Annotation -> SpoilerAnnotation.isSpoilerAnnotation(this)
      else -> false
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
