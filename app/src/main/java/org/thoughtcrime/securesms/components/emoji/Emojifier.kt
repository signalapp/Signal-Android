/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.emoji

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Applies Signal or System emoji to the given content based off user settings.
 *
 * Text is transformed and passed to content as an annotated string and inline content map.
 */
@Composable
fun Emojifier(
  text: String,
  useSystemEmoji: Boolean = !LocalInspectionMode.current && SignalStore.settings.isPreferSystemEmoji,
  content: @Composable (AnnotatedString, Map<String, InlineTextContent>) -> Unit = { annotatedText, inlineContent ->
    Text(
      text = annotatedText,
      inlineContent = inlineContent
    )
  }
) {
  if (useSystemEmoji) {
    content(buildAnnotatedString { append(text) }, emptyMap())
    return
  }

  val context = LocalContext.current
  val fontSize = LocalTextStyle.current.fontSize

  val foundEmojis: List<EmojiParser.Candidate> = remember(text) {
    EmojiProvider.getCandidates(text)?.list.orEmpty()
  }
  val inlineContentByEmoji: Map<String, InlineTextContent> = remember(text, fontSize) {
    foundEmojis.associate { it.drawInfo.emoji to createInlineContent(context, it.drawInfo.emoji, fontSize) }
  }

  val annotatedString = remember(text) { buildAnnotatedString(text, foundEmojis) }
  content(annotatedString, inlineContentByEmoji)
}

private fun createInlineContent(context: Context, emoji: String, fontSize: TextUnit): InlineTextContent {
  return InlineTextContent(
    placeholder = Placeholder(width = fontSize, height = fontSize, PlaceholderVerticalAlign.TextCenter)
  ) {
    Image(
      painter = rememberDrawablePainter(EmojiProvider.getEmojiDrawable(context, emoji)),
      contentDescription = null
    )
  }
}

/**
 * Constructs an [AnnotatedString] from [text], substituting each emoji in [foundEmojis] with an inline content placeholder.
 */
private fun buildAnnotatedString(
  text: String,
  foundEmojis: List<EmojiParser.Candidate>
): AnnotatedString = buildAnnotatedString {
  var nextSegmentStartIndex = 0

  foundEmojis.forEach { emoji ->
    if (emoji.startIndex > nextSegmentStartIndex) {
      append(text, start = nextSegmentStartIndex, end = emoji.startIndex)
    }
    appendInlineContent(emoji.drawInfo.emoji)
    nextSegmentStartIndex = emoji.endIndex
  }

  if (nextSegmentStartIndex < text.length) {
    append(text, start = nextSegmentStartIndex, end = text.length)
  }
}

@Composable
fun EmojiImage(
  emoji: String,
  modifier: Modifier
) {
  if (LocalInspectionMode.current) {
    Box(modifier.background(color = Color.Red, shape = CircleShape))
    return
  }

  val context = LocalContext.current
  val drawable = remember(emoji) { EmojiProvider.getEmojiDrawable(context, emoji) }
  val painter = rememberDrawablePainter(drawable)

  Image(
    painter = painter,
    contentDescription = emoji,
    modifier = modifier
  )
}

@Composable
@DayNightPreviews
private fun EmojifierPreview() {
  Previews.Preview {
    Emojifier(text = "This message has an emoji ❤\uFE0F")
  }
}
