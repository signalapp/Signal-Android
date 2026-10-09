/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.styledtext

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.emoji.EmojiDependencies
import org.signal.emoji.EmojiProvider
import org.signal.emoji.Emojifier
import org.signal.uicomponents.spoiler.SPOILER_ANNOTATION_TAG
import org.signal.uicomponents.spoiler.SpoilerState
import org.signal.uicomponents.spoiler.SpoilerText

/**
 * Renders a [StyledString] with Signal emoji, text styles, tappable links, and spoilers that stay hidden until tapped.
 *
 * @param suffix Appended after the text, outside of any of its ranges.
 * @param onTextLayout Receives the layout along with a way to map its offsets back to indices in [StyledString.text].
 */
@Composable
fun StyledText(
  text: StyledString,
  modifier: Modifier = Modifier,
  spoilerState: SpoilerState = remember(text) { SpoilerState() },
  style: TextStyle = LocalTextStyle.current,
  color: Color = Color.Unspecified,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  suffix: AnnotatedString? = null,
  onTextLayout: (StyledTextLayout) -> Unit = {}
) {
  val spoilers = remember(text.styles) { text.styles.filter { it.style == StyleRange.Style.SPOILER } }
  val hiddenSpoilers = spoilers.filterNot { spoilerState.isRevealed(it.spoilerId) }
  val visibleText = remember(text.text, hiddenSpoilers) { text.text.maskSpoilers(hiddenSpoilers) }

  val useSystemEmoji = LocalInspectionMode.current || EmojiDependencies.preferSystemEmoji
  val emojis = remember(visibleText, useSystemEmoji) {
    if (useSystemEmoji) {
      emptyList()
    } else {
      EmojiProvider.getCandidates(visibleText)?.list.orEmpty().map { it.startIndex until it.endIndex }
    }
  }

  Emojifier(text = visibleText, useSystemEmoji = useSystemEmoji) { emojified, inlineContent ->
    SpoilerText(
      text = remember(emojified, text, hiddenSpoilers, suffix) {
        buildStyledAnnotatedString(emojified, text, spoilers, hiddenSpoilers, emojis, suffix)
      },
      spoilerState = spoilerState,
      inlineContent = inlineContent,
      style = style,
      color = color,
      maxLines = maxLines,
      overflow = overflow,
      onTextLayout = { onTextLayout(StyledTextLayout(it, emojis)) },
      modifier = modifier
    )
  }
}

/**
 * The layout of a [StyledText]. Signal emoji collapse to a single placeholder character, so layout offsets
 * must go through [toTextIndex] before they can be used against [StyledString.text].
 */
class StyledTextLayout internal constructor(
  val textLayoutResult: TextLayoutResult,
  private val emojis: List<IntRange>
) {
  fun toTextIndex(layoutOffset: Int): Int = emojifiedIndexToRaw(layoutOffset, emojis)
}

private fun buildStyledAnnotatedString(
  emojified: AnnotatedString,
  text: StyledString,
  spoilers: List<StyleRange>,
  hiddenSpoilers: List<StyleRange>,
  emojis: List<IntRange>,
  suffix: AnnotatedString?
): AnnotatedString {
  return buildAnnotatedString {
    append(emojified)

    for (range in text.styles) {
      val spanStyle = range.style.toSpanStyle() ?: continue
      val start = rawIndexToEmojified(range.start, emojis)
      val end = rawIndexToEmojified(range.end, emojis).coerceAtMost(length)
      if (start < end) {
        addStyle(spanStyle, start, end)
      }
    }

    for (spoiler in spoilers) {
      val start = rawIndexToEmojified(spoiler.start, emojis)
      val end = rawIndexToEmojified(spoiler.end, emojis).coerceAtMost(length)
      if (start < end) {
        addStringAnnotation(SPOILER_ANNOTATION_TAG, spoiler.spoilerId, start, end)
      }
    }

    for (link in text.links) {
      if (hiddenSpoilers.any { link.start < it.end && it.start < link.end }) {
        continue
      }
      val start = rawIndexToEmojified(link.start, emojis)
      val end = rawIndexToEmojified(link.end, emojis)
      if (start < length && end <= length) {
        addLink(
          LinkAnnotation.Url(
            url = link.url,
            styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
          ),
          start,
          end
        )
      }
    }

    if (suffix != null) {
      append(suffix)
    }
  }
}

private val StyleRange.spoilerId: String
  get() = "$start-$end"

private fun StyleRange.Style.toSpanStyle(): SpanStyle? {
  return when (this) {
    StyleRange.Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    StyleRange.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    StyleRange.Style.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    StyleRange.Style.MONOSPACE -> SpanStyle(fontFamily = FontFamily.Monospace)
    StyleRange.Style.SPOILER -> null
  }
}

/** Replaces the non-whitespace characters in [spoilers] with figure spaces, keeping the text's length and line breaks. */
internal fun String.maskSpoilers(spoilers: List<StyleRange>): String {
  if (spoilers.isEmpty()) {
    return this
  }

  val chars = toCharArray()
  for (spoiler in spoilers) {
    for (i in spoiler.start until spoiler.end.coerceAtMost(chars.size)) {
      if (!chars[i].isWhitespace()) {
        chars[i] = SPOILER_MASK_CHAR
      }
    }
  }
  return String(chars)
}

/** An index inside an emoji maps to just past its placeholder, so a range ending there still covers the whole emoji. */
internal fun rawIndexToEmojified(index: Int, emojis: List<IntRange>): Int {
  var result = index
  for (emoji in emojis) {
    val emojiEnd = emoji.last + 1
    if (emojiEnd <= index) {
      result -= emojiEnd - emoji.first - 1
    } else if (emoji.first < index) {
      result -= index - emoji.first - 1
    } else {
      break
    }
  }
  return result
}

internal fun emojifiedIndexToRaw(index: Int, emojis: List<IntRange>): Int {
  var delta = 0
  for (emoji in emojis) {
    if (emoji.first - delta < index) {
      delta += emoji.last - emoji.first
    } else {
      break
    }
  }
  return index + delta
}

internal const val SPOILER_MASK_CHAR = ' '

@DayNightPreviews
@Composable
private fun StyledTextPreview() {
  Previews.Preview {
    StyledText(
      text = StyledString(
        text = "Bold, italic, struck, mono, a link, and a secret spoiler.",
        styles = listOf(
          StyleRange(0, 4, StyleRange.Style.BOLD),
          StyleRange(6, 12, StyleRange.Style.ITALIC),
          StyleRange(14, 20, StyleRange.Style.STRIKETHROUGH),
          StyleRange(22, 26, StyleRange.Style.MONOSPACE),
          StyleRange(49, 56, StyleRange.Style.SPOILER)
        ),
        links = listOf(LinkRange(30, 34, "https://signal.org"))
      ),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(16.dp)
    )
  }
}
