/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.graphics.Typeface
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import androidx.core.text.getSpans
import org.signal.uicomponents.styledtext.LinkRange
import org.signal.uicomponents.styledtext.StyleRange
import org.signal.uicomponents.styledtext.StyledString
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation

/**
 * Maps our app-specific style types to the types our generic UI component uses.
 */
fun CharSequence.toStyledString(): StyledString {
  val spanned = SpannableString(this)
  spanned.getSpans<Annotation>().forEach {
    if (MentionAnnotation.isMentionAnnotation(it)) {
      spanned.removeSpan(it)
    }
  }

  val links = spanned.getSpans<URLSpan>().mapNotNull { span ->
    spanned.validRange(span)?.let { (start, end) -> LinkRange(start, end, span.url) }
  }

  val revealedSpoilers = SpoilerAnnotation.getSpoilerAndClickAnnotations(spanned)
    .filterValues { it?.spoilerRevealed == true }
    .keys

  val styles = spanned.getSpans<Any>().flatMap { span ->
    val range = spanned.validRange(span) ?: return@flatMap emptyList()
    val styles = when {
      span is StyleSpan && span.style == Typeface.BOLD -> listOf(StyleRange.Style.BOLD)
      span is StyleSpan && span.style == Typeface.ITALIC -> listOf(StyleRange.Style.ITALIC)
      span is StyleSpan && span.style == Typeface.BOLD_ITALIC -> listOf(StyleRange.Style.BOLD, StyleRange.Style.ITALIC)
      span is StrikethroughSpan -> listOf(StyleRange.Style.STRIKETHROUGH)
      span is TypefaceSpan && span.family == MessageStyler.MONOSPACE -> listOf(StyleRange.Style.MONOSPACE)
      span is Annotation && SpoilerAnnotation.isSpoilerAnnotation(span) && span !in revealedSpoilers -> listOf(StyleRange.Style.SPOILER)
      else -> emptyList()
    }
    styles.map { StyleRange(range.first, range.second, it) }
  }

  return StyledString(text = spanned.toString(), links = links, styles = styles)
}

private fun Spanned.validRange(span: Any): Pair<Int, Int>? {
  val start = getSpanStart(span)
  val end = getSpanEnd(span)
  return if (start in 0 until end && end <= length) start to end else null
}
