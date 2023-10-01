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
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList.BodyRange
import org.thoughtcrime.securesms.util.PlaceholderURLSpan
import java.lang.Integer.max
import java.lang.Integer.min

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
    var bottomButton: BodyRange.Button? = null

    messageRanges
      .ranges
      .filter { r -> r.start >= 0 && r.start < span.length && r.start + r.length >= 0 }
      .forEach { range ->
        val start = range.start
        val end = (range.start + range.length).coerceAtMost(span.length)

        if (range.style != null) {
          val styleSpan: Any? = when (range.style) {
            BodyRange.Style.BOLD -> boldStyle()
            BodyRange.Style.ITALIC -> italicStyle()
            BodyRange.Style.STRIKETHROUGH -> strikethroughStyle()
            BodyRange.Style.MONOSPACE -> monoStyle()
            BodyRange.Style.SPOILER -> {
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
        } else if (range.link != null) {
          span.setSpan(PlaceholderURLSpan(range.link), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          hasLinks = true
        } else if (range.button != null) {
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
  fun toggleStyle(style: BodyRange.Style, text: Spannable, start: Int, end: Int) {
    if (start >= end) {
      return
    }

    val toggleRange = start..end
    val spanAndRanges = text
      .getSpans(start, end, Object::class.java)
      .asSequence()
      .filter { it.isStyle(style) }
      .map { SpanAndRange(it, text.getSpanStart(it)..text.getSpanEnd(it)) }
      .toMutableList()

    val isForceAdditive = spanAndRanges.hasGapsBetween(toggleRange)
    var shouldAddRange = true

    val iterator = spanAndRanges.iterator()
    while (iterator.hasNext()) {
      val existingStyle = iterator.next()
      if (toggleRange == existingStyle.range) {
        text.removeSpan(existingStyle.span)
        iterator.remove()
        shouldAddRange = false
      } else if (toggleRange.containedIn(existingStyle.range)) {
        text.removeSpan(existingStyle.span)
        iterator.remove()
        text.setSpan(style.toStyleSpan(existingStyle.range.first, toggleRange.first), existingStyle.range.first, toggleRange.first, SPAN_FLAGS)
        text.setSpan(style.toStyleSpan(toggleRange.last, existingStyle.range.last), toggleRange.last, existingStyle.range.last, SPAN_FLAGS)
        shouldAddRange = false
        break
      } else if (toggleRange.covers(existingStyle.range) && isForceAdditive) {
        text.removeSpan(existingStyle.span)
        iterator.remove()
      }
    }

    if (shouldAddRange) {
      val styleSpan = style.toStyleSpan(start, end - start)
      text.setSpan(styleSpan, start, end, SPAN_FLAGS)
      spanAndRanges += SpanAndRange(styleSpan, start..end)
    }

    spanAndRanges.sortWith { (_, lhs), (_, rhs) ->
      val compareStart = lhs.first.compareTo(rhs.first)
      if (compareStart == 0) {
        lhs.last.compareTo(rhs.last)
      } else {
        compareStart
      }
    }

    var index = 0
    while (index < spanAndRanges.size) {
      val spanAndRange = spanAndRanges[index]
      val nextSpanAndRange = if (index < spanAndRanges.lastIndex) spanAndRanges[index + 1] else null
      if (spanAndRange.range.first == spanAndRange.range.last) {
        text.removeSpan(spanAndRange.span)
        spanAndRanges.removeAt(index)
      } else if (nextSpanAndRange != null && spanAndRange.range.overlapsStart(nextSpanAndRange.range)) {
        text.removeSpan(nextSpanAndRange.span)
        spanAndRanges.removeAt(index + 1)
        text.removeSpan(spanAndRange.span)
        spanAndRanges.removeAt(index)

        val mergedRange = min(nextSpanAndRange.range.first, spanAndRange.range.first)..max(nextSpanAndRange.range.last, spanAndRange.range.last)
        val styleSpan = style.toStyleSpan(mergedRange.first, mergedRange.last - mergedRange.first)
        text.setSpan(styleSpan, mergedRange.first, mergedRange.last, SPAN_FLAGS)
        spanAndRanges.add(index, SpanAndRange(styleSpan, mergedRange))
      } else {
        index++
      }
    }
  }

  @JvmStatic
  fun clearStyling(text: Spannable, start: Int, end: Int) {
    val clearRange = start..end

    text
      .getSpans(start, end, Object::class.java)
      .asSequence()
      .filter { it.isSupportedStyle() }
      .map { SpanAndRange(it, text.getSpanStart(it)..text.getSpanEnd(it)) }
      .forEach { spanAndRange ->
        if (clearRange.covers(spanAndRange.range)) {
          text.removeSpan(spanAndRange.span)
        } else if (clearRange.containedIn(spanAndRange.range)) {
          text.removeSpan(spanAndRange.span)
          text.setSpan(copyStyleSpan(spanAndRange.span, spanAndRange.range.first, clearRange.first), spanAndRange.range.first, clearRange.first, SPAN_FLAGS)
          text.setSpan(copyStyleSpan(spanAndRange.span, clearRange.last, spanAndRange.range.last - clearRange.last), clearRange.last, spanAndRange.range.last, SPAN_FLAGS)
        } else if (clearRange.overlapsStart(spanAndRange.range)) {
          text.removeSpan(spanAndRange.span)
          text.setSpan(copyStyleSpan(spanAndRange.span, clearRange.last, spanAndRange.range.last), clearRange.last, spanAndRange.range.last, SPAN_FLAGS)
        } else if (clearRange.overlapsEnd(spanAndRange.range)) {
          text.removeSpan(spanAndRange.span)
          text.setSpan(copyStyleSpan(spanAndRange.span, spanAndRange.range.first, clearRange.first), spanAndRange.range.first, clearRange.first, SPAN_FLAGS)
        }
      }
  }

  @JvmStatic
  @JvmOverloads
  fun hasStyling(text: Spanned, start: Int = 0, end: Int = text.length): Boolean {
    return text
      .getSpans(start, end, Object::class.java)
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

          val style: BodyRange.Style? = when (span) {
            is StyleSpan -> {
              when (span.style) {
                Typeface.BOLD -> BodyRange.Style.BOLD
                Typeface.ITALIC -> BodyRange.Style.ITALIC
                else -> null
              }
            }
            is StrikethroughSpan -> BodyRange.Style.STRIKETHROUGH
            is TypefaceSpan -> BodyRange.Style.MONOSPACE
            is Annotation -> {
              if (SpoilerAnnotation.isSpoilerAnnotation(span)) {
                BodyRange.Style.SPOILER
              } else {
                null
              }
            }
            else -> throw IllegalArgumentException("Provided text contains unsupported spans")
          }

          if (spanLength > 0 && style != null) {
            BodyRange(start = spanStart, length = spanLength, style = style)
          } else {
            null
          }
        }
    } else {
      emptyList()
    }

    return if (bodyRanges.isNotEmpty()) {
      BodyRangeList(ranges = bodyRanges)
    } else {
      null
    }
  }

  @JvmStatic
  fun convertSpoilersToComposeMode(text: Spannable) {
    SpoilerAnnotation.getSpoilerAndClickAnnotations(text)
      .forEach { (spoiler, clickSpan) ->
        val start = text.getSpanStart(spoiler)
        val end = text.getSpanEnd(spoiler)
        val convertedSpoiler = copyStyleSpan(spoiler, start, end - start)
        text.removeSpan(spoiler)
        text.setSpan(convertedSpoiler, start, end, SPAN_FLAGS)

        if (clickSpan != null) {
          text.removeSpan(clickSpan)
        }
      }
  }

  fun Any.isSupportedStyle(): Boolean {
    return when (this) {
      is CharacterStyle -> isSupportedCharacterStyle()
      is Annotation -> SpoilerAnnotation.isSpoilerAnnotation(this)
      else -> false
    }
  }

  private fun Any.isSupportedCharacterStyle(): Boolean {
    return when (this) {
      is StyleSpan -> style == Typeface.ITALIC || style == Typeface.BOLD
      is StrikethroughSpan -> true
      is TypefaceSpan -> family == MONOSPACE
      else -> false
    }
  }

  private fun Any.isStyle(style: BodyRange.Style): Boolean {
    return when (this) {
      is CharacterStyle -> isCharacterStyle(style)
      is Annotation -> SpoilerAnnotation.isSpoilerAnnotation(this) && style == BodyRange.Style.SPOILER
      else -> false
    }
  }

  private fun CharacterStyle.isCharacterStyle(style: BodyRange.Style): Boolean {
    return when (this) {
      is StyleSpan -> (this.style == Typeface.ITALIC && style == BodyRange.Style.ITALIC) || (this.style == Typeface.BOLD && style == BodyRange.Style.BOLD)
      is StrikethroughSpan -> style == BodyRange.Style.STRIKETHROUGH
      is TypefaceSpan -> this.family == MONOSPACE && style == BodyRange.Style.MONOSPACE
      else -> false
    }
  }

  private fun copyStyleSpan(span: Any, start: Int, length: Int): Any? {
    return when (span) {
      is StyleSpan -> {
        when (span.style) {
          Typeface.BOLD -> boldStyle()
          Typeface.ITALIC -> italicStyle()
          else -> null
        }
      }
      is StrikethroughSpan -> strikethroughStyle()
      is TypefaceSpan -> monoStyle()
      is Annotation -> {
        if (SpoilerAnnotation.isSpoilerAnnotation(span)) {
          spoilerStyle(COMPOSE_ID, start, length)
        } else {
          null
        }
      }
      else -> throw IllegalArgumentException("Provided text contains unsupported spans")
    }
  }

  private fun BodyRange.Style.toStyleSpan(start: Int, length: Int): Any {
    return when (this) {
      BodyRange.Style.BOLD -> boldStyle()
      BodyRange.Style.ITALIC -> italicStyle()
      BodyRange.Style.SPOILER -> spoilerStyle(COMPOSE_ID, start, length)
      BodyRange.Style.STRIKETHROUGH -> strikethroughStyle()
      BodyRange.Style.MONOSPACE -> monoStyle()
      else -> throw IllegalArgumentException()
    }
  }

  data class Result(val hasStyleLinks: Boolean = false, val bottomButton: BodyRange.Button? = null) {
    companion object {
      @JvmStatic
      val NO_STYLE = Result()

      @JvmStatic
      fun none(): Result = NO_STYLE
    }
  }

  private data class SpanAndRange(val span: Any, val range: IntRange)

  private fun IntRange.overlapsStart(other: IntRange): Boolean {
    return this.first <= other.first && this.last > other.first
  }

  private fun IntRange.overlapsEnd(other: IntRange): Boolean {
    return this.first < other.last && this.last >= other.last
  }

  private fun IntRange.containedIn(other: IntRange): Boolean {
    return this.first >= other.first && this.last <= other.last
  }

  private fun IntRange.covers(other: IntRange): Boolean {
    return this.first <= other.first && this.last >= other.last
  }

  /**
   * Checks if a sorted, non-overlapping list of ranges does not cover the provided [toggleRange] completely. That is,
   * there is a value that does not exists in any of the ranges in the list but does exist in [toggleRange]
   *
   * For example, a list of ranges [[0..5], [7..10]] and a toggle range of [0..10] has a gap for the value 6. If
   * the list was [[0..5], [6..8], [9..10]] there would be no gaps.
   */
  private fun List<SpanAndRange>.hasGapsBetween(toggleRange: IntRange): Boolean {
    val startingRangeIndex = indexOfFirst { it.range.first <= toggleRange.first && it.range.last >= toggleRange.first }
    if (startingRangeIndex == -1) {
      return true
    }

    val endingRangeIndex = indexOfFirst { it.range.first <= toggleRange.last && it.range.last >= toggleRange.last }
    if (endingRangeIndex == -1) {
      return true
    }

    for (i in startingRangeIndex until endingRangeIndex) {
      if (this[i].range.last != this[i + 1].range.first) {
        return true
      }
    }

    return false
  }
}
