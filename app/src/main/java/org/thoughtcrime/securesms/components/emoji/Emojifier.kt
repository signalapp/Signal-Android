/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.emoji

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview

/**
 * Applies Signal or System emoji to the given content based off user settings.
 *
 * Text is transformed and passed to content as an annotated string and inline content map.
 */
@Composable
fun Emojifier(
  text: String,
  content: @Composable (AnnotatedString, Map<String, InlineTextContent>) -> Unit = { annotatedText, inlineContent ->
    Text(
      text = annotatedText,
      inlineContent = inlineContent
    )
  }
) {
  if (LocalInspectionMode.current) {
    content(buildAnnotatedString { append(text) }, emptyMap())
    return
  }

  val context = LocalContext.current
  val candidates = remember(text) { EmojiProvider.getCandidates(text) }
  val candidateMap: Map<String, InlineTextContent> = remember(text) {
    candidates?.associate { candidate ->
      candidate.drawInfo.emoji to InlineTextContent(placeholder = Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)) {
        Image(
          painter = rememberDrawablePainter(EmojiProvider.getEmojiDrawable(context, candidate.drawInfo.emoji)),
          contentDescription = null
        )
      }
    } ?: emptyMap()
  }

  val annotatedString = buildAnnotatedString {
    append(text)

    candidates?.forEach {
      addStringAnnotation(
        tag = "EMOJI",
        annotation = it.drawInfo.emoji,
        start = it.startIndex,
        end = it.endIndex
      )
    }
  }

  content(annotatedString, candidateMap)
}

@Composable
@SignalPreview
private fun EmojifierPreview() {
  Previews.Preview {
    Emojifier(text = "This message has an emoji ❤\uFE0F")
  }
}
