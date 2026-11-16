/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.emoji

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import java.text.BreakIterator

/**
 * The text-cutting rules [EmojiTextView] uses to fit text within a length or line limit.
 */
internal object EmojiTextTruncation {

  const val ELLIPSIS = '…'

  /**
   * [text] up to [cut], without trailing whitespace, followed by [suffix]. Spans that would otherwise grow onto the
   * suffix are closed at the cut.
   */
  fun truncate(text: CharSequence, cut: Int, suffix: CharSequence): CharSequence {
    val end = trimTrailingWhitespace(text, 0, snapCut(text, cut))
    val truncated = SpannableStringBuilder(text, 0, end)

    for (span in truncated.getSpans(end, end, Any::class.java)) {
      val flags = truncated.getSpanFlags(span)
      if (truncated.getSpanEnd(span) == end && flags and Spanned.SPAN_POINT_MARK_MASK != Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) {
        val start = truncated.getSpanStart(span)
        truncated.removeSpan(span)
        truncated.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    }

    return truncated.append(suffix)
  }

  /**
   * Moves [cut] back so it doesn't split a character cluster, an emoji span, or a mention.
   */
  fun snapCut(text: CharSequence, cut: Int, characters: BreakIterator = characterIterator(text)): Int {
    var snapped = cut.coerceIn(0, text.length)

    if (snapped in 1 until text.length) {
      if (!characters.isBoundary(snapped)) {
        snapped = characters.preceding(snapped).coerceAtLeast(0)
      }
    }

    if (text is Spanned) {
      val covering = text.getSpans(snapped, snapped, ReplacementSpan::class.java).toList<Any>() +
        MentionAnnotation.getMentionAnnotations(text, snapped, snapped)

      for (span in covering) {
        val start = text.getSpanStart(span)
        if (start < snapped && text.getSpanEnd(span) > snapped) {
          snapped = start
        }
      }
    }

    return snapped
  }

  /** Character-cluster boundaries for [text], built once and passed to [snapCut] when snapping many cuts in the same text. */
  fun characterIterator(text: CharSequence): BreakIterator {
    return BreakIterator.getCharacterInstance().apply { setText(text.toString()) }
  }

  /**
   * The largest cut in `[lineStart, lineEnd]` for which [fits] holds, assuming wider cuts never fit once a narrower
   * one doesn't. Returns [lineStart] if nothing fits.
   */
  fun largestFittingCut(text: CharSequence, lineStart: Int, lineEnd: Int, fits: (cut: Int) -> Boolean): Int {
    var low = lineStart
    var high = trimTrailingWhitespace(text, lineStart, lineEnd)

    if (fits(high)) {
      return high
    }

    while (low < high) {
      val mid = (low + high + 1) ushr 1
      if (fits(mid)) {
        low = mid
      } else {
        high = mid - 1
      }
    }

    return low
  }

  /**
   * The start of the word before [cut], for backing off when a cut still wraps onto an extra line.
   */
  fun previousWordBoundary(text: CharSequence, cut: Int): Int {
    val end = trimTrailingWhitespace(text, 0, cut)
    var start = end
    while (start > 0 && !text[start - 1].isWhitespace()) {
      start--
    }
    return if (start > 0) start else (end - 1).coerceAtLeast(0)
  }

  fun trimTrailingWhitespace(text: CharSequence, start: Int, end: Int): Int {
    var trimmed = end
    while (trimmed > start && text[trimmed - 1].isWhitespace()) {
      trimmed--
    }
    return trimmed
  }
}
