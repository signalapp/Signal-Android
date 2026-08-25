/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.emoji.Emojifier

object MemberLabelPill {
  val contentPaddingNormal = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
  val contentPaddingCompact = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

  @get:Composable
  val textStyleCompact: TextStyle
    get() = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal)

  @get:Composable
  val textStyleNormal: TextStyle
    get() = MaterialTheme.typography.bodyLarge
}

/**
 * Displays member label text with an optional emoji.
 */
@Composable
fun MemberLabelPill(
  emoji: String?,
  text: String,
  tintColor: Color,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = MemberLabelPill.contentPaddingNormal,
  textStyle: TextStyle = MemberLabelPill.textStyleCompact,
  maxLines: Int = 1
) {
  val isDark = isSystemInDarkTheme()
  val backgroundColor = remember(isDark, tintColor) {
    tintColor.copy(alpha = if (isDark) 0.32f else 0.10f)
  }

  val textColor = remember(isDark, tintColor) {
    if (isDark) {
      Color.White.copy(alpha = 0.25f).compositeOver(tintColor)
    } else {
      Color.Black.copy(alpha = 0.30f).compositeOver(tintColor)
    }
  }

  MemberLabelPill(
    emoji = emoji,
    text = text,
    textColor = textColor,
    backgroundColor = backgroundColor,
    modifier = modifier,
    contentPadding = contentPadding,
    textStyle = textStyle,
    maxLines = maxLines
  )
}

/**
 * Displays member label text with an optional emoji.
 */
@Composable
fun MemberLabelPill(
  emoji: String?,
  text: String,
  textColor: Color,
  backgroundColor: Color,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = MemberLabelPill.contentPaddingNormal,
  textStyle: TextStyle = MemberLabelPill.textStyleCompact,
  maxLines: Int = 1
) {
  val shape = if (maxLines > 1) RoundedCornerShape(24.dp) else RoundedCornerShape(percent = 50)

  Row(
    modifier = modifier
      .clip(shape)
      .background(
        color = backgroundColor,
        shape = shape
      )
      .padding(contentPadding),
    verticalAlignment = Alignment.CenterVertically
  ) {
    ProvideTextStyle(textStyle) {
      if (!emoji.isNullOrEmpty()) {
        Emojifier(text = emoji) { annotatedText, inlineContent ->
          Text(
            text = annotatedText,
            inlineContent = inlineContent,
            modifier = if (text.isNotEmpty()) Modifier.padding(end = 4.dp) else Modifier
          )
        }
      }

      if (text.isNotEmpty()) {
        Emojifier(text = text) { annotatedText, inlineContent ->
          Text(
            text = annotatedText,
            inlineContent = inlineContent,
            color = textColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun MemberLabelWithEmojiPreview() = Previews.Preview {
  Box(modifier = Modifier.width(200.dp)) {
    MemberLabelPill(
      emoji = "🧠",
      text = "Zero-Knowledge Know-It-All",
      tintColor = Color(0xFF7A3DF5)
    )
  }
}

@DayNightPreviews
@Composable
private fun MemberLabelTextOnlyPreview() = Previews.Preview {
  Box(modifier = Modifier.width(200.dp)) {
    MemberLabelPill(
      emoji = null,
      text = "Zero-Knowledge Know-It-All",
      tintColor = Color(0xFF7A3DF5)
    )
  }
}
