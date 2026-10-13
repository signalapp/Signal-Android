/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.R
import org.signal.mediasend.test.TestTags

/** The circle itself, which anything sharing its row lines up against. */
internal val NEXT_BUTTON_CIRCLE_SIZE = 40.dp

/** The circle is smaller than a finger, so the touch target is grown around it rather than the circle drawn bigger. */
internal val NEXT_BUTTON_TOUCH_TARGET = 48.dp

private val NEXT_BUTTON_ICON_SIZE = 24.dp
private val NEXT_COUNT_HEIGHT = 18.dp
private val NEXT_COUNT_HORIZONTAL_PADDING = 6.dp

/**
 * How much of the count clears the touch target once half of it is over the circle's top edge. Padding the button by it
 * puts the count's middle exactly on that edge.
 */
private val NEXT_COUNT_OVERHANG = NEXT_COUNT_HEIGHT / 2 - (NEXT_BUTTON_TOUCH_TARGET - NEXT_BUTTON_CIRCLE_SIZE) / 2

/** The whole button, count and all, which is the room a row has to leave for it. */
internal val NEXT_BUTTON_HEIGHT = NEXT_BUTTON_TOUCH_TARGET + NEXT_COUNT_OVERHANG

/**
 * Moves the flow on to the editor and says how much is waiting there. The count straddles the circle's top edge, which
 * keeps it clear of whatever the button is floating over.
 *
 * The fill defaults to the theme, which is what the picker's bottom bar wants. The capture screen hands in the camera's
 * translucent control background instead, so the viewfinder reads through the circle. Only the count takes a color from
 * the send itself.
 *
 * @param selectedMediaCount How much the editor has waiting for it
 * @param recipientChatColor The color of the one conversation this is headed to, or null for a send with no single
 *   destination, which leaves the count on the theme's own color.
 */
@Composable
internal fun NextButton(
  selectedMediaCount: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  recipientChatColor: Color? = null,
  containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
  contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
  Box(modifier = modifier.widthIn(min = NEXT_BUTTON_TOUCH_TARGET)) {
    IconButton(
      onClick = onClick,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(top = NEXT_COUNT_OVERHANG)
        .size(NEXT_BUTTON_TOUCH_TARGET)
        .testTag(TestTags.MEDIA_SEND_NEXT_BUTTON)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(NEXT_BUTTON_CIRCLE_SIZE)
          .background(color = containerColor, shape = CircleShape)
      ) {
        Icon(
          imageVector = SignalIcons.ArrowEnd.imageVector,
          contentDescription = stringResource(R.string.MediaSelectScreen__next),
          tint = contentColor,
          modifier = Modifier.size(NEXT_BUTTON_ICON_SIZE)
        )
      }
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .heightIn(min = NEXT_COUNT_HEIGHT)
        .widthIn(min = NEXT_COUNT_HEIGHT)
        .background(color = recipientChatColor ?: MaterialTheme.colorScheme.primary, shape = CircleShape)
        .padding(horizontal = NEXT_COUNT_HORIZONTAL_PADDING)
    ) {
      Text(
        text = selectedMediaCount.toString(),
        color = if (recipientChatColor != null) SignalTheme.colors.colorOnCustom else MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier.testTag(TestTags.MEDIA_SEND_MEDIA_COUNT)
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun NextButtonPreview() {
  Previews.Preview {
    NextButton(selectedMediaCount = 1, onClick = {})
  }
}

/** A count wide enough to push past the circle it straddles, which widens the button. */
@DayNightPreviews
@Composable
private fun NextButtonWideCountPreview() {
  Previews.Preview {
    NextButton(selectedMediaCount = 12, onClick = {})
  }
}

/** A send headed to one conversation, so the count carries that conversation's color. */
@DayNightPreviews
@Composable
private fun NextButtonChatColorPreview() {
  Previews.Preview {
    NextButton(selectedMediaCount = 3, onClick = {}, recipientChatColor = Color(0xFF3B7845))
  }
}
