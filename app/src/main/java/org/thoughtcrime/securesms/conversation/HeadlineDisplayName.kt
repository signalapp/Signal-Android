/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.LargeFontPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.Emojifier
import org.thoughtcrime.securesms.fonts.SignalSymbols
import org.thoughtcrime.securesms.fonts.SignalSymbols.SignalSymbol

private const val VERIFIED_BADGE_ID = "verified_badge"

/**
 * Compose-native version of [org.thoughtcrime.securesms.recipients.Recipient.getDisplayNameForHeadline].
 */
@Composable
fun HeadlineDisplayName(
  displayName: String,
  showVerified: Boolean,
  isSystemContact: Boolean,
  showChevron: Boolean,
  modifier: Modifier = Modifier
) {
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val chevronGlyph = if (isLtr) SignalSymbols.Glyph.CHEVRON_RIGHT else SignalSymbols.Glyph.CHEVRON_LEFT
  val outlineColor = MaterialTheme.colorScheme.outline
  val badgeOffset = with(LocalDensity.current) { (-1).sp.toDp() }

  Emojifier(text = displayName) { emojiText, emojiInlineContent ->
    val styledText = buildAnnotatedString {
      if (!isLtr) {
        if (showChevron) {
          SignalSymbol(chevronGlyph, fontSize = 18.sp, color = outlineColor, baselineShift = BaselineShift(0.1f))
          append("\u00A0")
        }
        if (showVerified) {
          appendInlineContent(VERIFIED_BADGE_ID)
          append("\u00A0")
        } else if (isSystemContact) {
          SignalSymbol(SignalSymbols.Glyph.PERSON_CIRCLE, fontSize = 18.sp, baselineShift = BaselineShift(0.1f))
          append("\u00A0")
        }
      }

      append(emojiText)

      if (isLtr) {
        if (showVerified) {
          append("\u00A0")
          appendInlineContent(VERIFIED_BADGE_ID)
        } else if (isSystemContact) {
          append("\u00A0")
          SignalSymbol(SignalSymbols.Glyph.PERSON_CIRCLE, fontSize = 18.sp, baselineShift = BaselineShift(0.1f))
        }
        if (showChevron) {
          append("\u00A0")
          SignalSymbol(chevronGlyph, fontSize = 18.sp, color = outlineColor, baselineShift = BaselineShift(0.1f))
        }
      }
    }

    val inlineContent = if (showVerified) {
      emojiInlineContent + mapOf(
        VERIFIED_BADGE_ID to InlineTextContent(
          placeholder = Placeholder(width = 22.sp, height = 22.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
        ) {
          Image(
            painter = painterResource(R.drawable.ic_official_28),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().offset(y = badgeOffset)
          )
        }
      )
    } else {
      emojiInlineContent
    }

    Text(
      text = styledText,
      inlineContent = inlineContent,
      style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr),
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      modifier = modifier
    )
  }
}

@DayNightPreviews
@Composable
private fun HeadlineDisplayNamePreview() = Previews.Preview {
  HeadlineDisplayName(
    displayName = "Katie Hall",
    showVerified = false,
    isSystemContact = false,
    showChevron = true
  )
}

@DayNightPreviews
@Composable
private fun HeadlineDisplayNameVerifiedPreview() = Previews.Preview {
  HeadlineDisplayName(
    displayName = "Katie Hall",
    showVerified = true,
    isSystemContact = false,
    showChevron = true
  )
}

@DayNightPreviews
@Composable
private fun HeadlineDisplayNameSystemContactPreview() = Previews.Preview {
  HeadlineDisplayName(
    displayName = "Katie Hall",
    showVerified = false,
    isSystemContact = true,
    showChevron = true
  )
}

@DayNightPreviews
@Composable
private fun HeadlineDisplayNameLongTextChevronPreview() = Previews.Preview {
  HeadlineDisplayName(
    displayName = "J. Jonah Jameson Jr.",
    showVerified = false,
    isSystemContact = false,
    showChevron = true,
    modifier = Modifier.width(120.dp)
  )
}

@DayNightPreviews
@Composable
private fun HeadlineDisplayNameLongTextSystemContactPreview() = Previews.Preview {
  HeadlineDisplayName(
    displayName = "J. Jonah Jameson Jr.",
    showVerified = false,
    isSystemContact = true,
    showChevron = true,
    modifier = Modifier.width(120.dp)
  )
}

@LargeFontPreviews
@Composable
private fun HeadlineDisplayNameLargeFontChevronPreview() = Previews.Preview {
  HeadlineDisplayName(
    displayName = "Katie Hall",
    showVerified = false,
    isSystemContact = false,
    showChevron = true
  )
}

@LargeFontPreviews
@Composable
private fun HeadlineDisplayNameLargeFontSystemContactPreview() = Previews.Preview {
  HeadlineDisplayName(
    displayName = "Katie Hall",
    showVerified = true,
    isSystemContact = true,
    showChevron = true
  )
}
