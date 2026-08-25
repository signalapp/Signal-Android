/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.emoji

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** [Text] that renders any emoji in [text] as inline content. */
@Composable
fun EmojiText(
  text: String,
  modifier: Modifier = Modifier,
  style: TextStyle = MaterialTheme.typography.bodyLarge,
  color: Color = MaterialTheme.colorScheme.onSurface,
  textAlign: TextAlign? = null,
  maxLines: Int = Int.MAX_VALUE
) {
  Emojifier(text = text) { annotatedText, inlineContent ->
    Text(
      text = annotatedText,
      inlineContent = inlineContent,
      style = style,
      color = color,
      textAlign = textAlign,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
      modifier = modifier
    )
  }
}
