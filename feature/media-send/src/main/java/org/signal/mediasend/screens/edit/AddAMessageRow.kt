/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.R
import org.signal.mediasend.test.TestTags

/** Mirrors the legacy send button's disabled tint, which has no equivalent in the core-ui color scheme. */
private val DisabledNextButtonColor = Color(0xFF777777)

/**
 * Mirrors the legacy send button's size. Has to be stated rather than left to the [IconButtons.IconButton] default of
 * 40dp: the default draws the container inside the 48dp of layout that [androidx.compose.material3.minimumInteractiveComponentSize]
 * reserves, leaving a button that is 8dp smaller than it looks like it should be and smaller than the row it sits in.
 */
private val NextButtonSize = 48.dp

/**
 * Because we need to be able to support stuff like mentions, styled text, and custom emoji, we need to allow
 * the users of this feature to inject their own text-field.
 */
val LocalAddAMessageRowTextField = compositionLocalOf<@Composable (CharSequence, Modifier) -> Unit> {
  { message, modifier ->
    Text(
      text = message.toString(),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = modifier
    )
  }
}

/**
 * @param recipientChatColor The chat color of the single recipient this media is headed to, or null when the destination
 *   is still to be chosen. Non-null makes the trailing button a chat-color-tinted send button rather than a themed
 *   "next" arrow.
 */
@Composable
fun AddAMessageRow(
  message: CharSequence?,
  onEvent: (MediaEditScreenEvents) -> Unit,
  onNextClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  canScheduleSend: Boolean = false,
  viewOnceAvailable: Boolean = false,
  viewOnce: Boolean = false,
  isReply: Boolean = false,
  recipientChatColor: Color? = null
) {
  Row(
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(24.dp))
        .weight(1f)
        .heightIn(min = 44.dp)
        .then(
          if (viewOnce) {
            // A view-once send cannot carry a body, so the row becomes a static label rather than an entry point.
            Modifier
          } else {
            Modifier.clickable(enabled = enabled, onClickLabel = stringResource(if (isReply) R.string.AddAMessageRow__add_a_reply else R.string.AddAMessageRow__add_a_message), onClick = { onEvent(MediaEditScreenEvents.AddMessageClick()) }, role = Role.Button)
          }
        )
    ) {
      if (viewOnce) {
        Text(
          text = stringResource(R.string.AddAMessageRow__view_once_media),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 16.dp)
        )
      } else {
        IconButtons.IconButton(
          enabled = enabled,
          onClick = { onEvent(MediaEditScreenEvents.AddMessageClick(startWithEmojiKeyboard = true)) }
        ) {
          Icon(
            painter = SignalIcons.Emoji.painter,
            contentDescription = stringResource(R.string.AddAMessageRow__open_emoji_keyboard)
          )
        }

        LocalAddAMessageRowTextField.current(
          message?.takeIf { it.isNotBlank() } ?: stringResource(if (isReply) R.string.AddAMessageRow__add_a_reply else R.string.AddAMessageRow__message),
          Modifier
            .weight(1f)
            .height(44.dp)
            .padding(end = if (viewOnceAvailable) 0.dp else 16.dp, top = 10.dp, bottom = 10.dp)
        )
      }

      if (viewOnceAvailable) {
        IconButtons.IconButton(
          enabled = enabled,
          onClick = { onEvent(MediaEditScreenEvents.ToggleViewOnce) }
        ) {
          Icon(
            painter = if (viewOnce) SignalIcons.ViewOnce.painter else SignalIcons.ViewOnceInfinite.painter,
            contentDescription = stringResource(R.string.AddAMessageRow__toggle_view_once)
          )
        }
      }
    }

    Box {
      val scheduleSendMenuController = remember { DropdownMenus.MenuController() }

      IconButtons.IconButton(
        enabled = enabled,
        onClick = onNextClick,
        size = NextButtonSize,
        onLongClick = if (canScheduleSend) scheduleSendMenuController::show else null,
        onLongClickLabel = stringResource(R.string.AddAMessageRow__schedule_send),
        colors = IconButtons.iconButtonColors(
          containerColor = recipientChatColor ?: MaterialTheme.colorScheme.primaryContainer,
          contentColor = if (recipientChatColor != null) SignalTheme.colors.colorOnCustom else MaterialTheme.colorScheme.onPrimaryContainer,
          disabledContainerColor = DisabledNextButtonColor,
          disabledContentColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
          .testTag(TestTags.ADD_A_MESSAGE_NEXT_BUTTON)
          .padding(start = 12.dp)
      ) {
        Icon(
          painter = if (recipientChatColor != null) SignalIcons.SendFill.painter else SignalIcons.ArrowEnd.painter,
          contentDescription = stringResource(if (recipientChatColor != null) R.string.AddAMessageRow__send else R.string.AddAMessageRow__next)
        )
      }

      ScheduleSendMenu(
        controller = scheduleSendMenuController,
        onOptionClick = { onEvent(MediaEditScreenEvents.ScheduleSendClick(it)) }
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun AddAMessageRowPreview() {
  Previews.Preview {
    AddAMessageRow(
      message = null,
      onEvent = {},
      onNextClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun AddAMessageRowViewOnceAvailablePreview() {
  Previews.Preview {
    AddAMessageRow(
      message = null,
      onEvent = {},
      onNextClick = {},
      viewOnceAvailable = true
    )
  }
}

@DayNightPreviews
@Composable
private fun AddAMessageRowViewOncePreview() {
  Previews.Preview {
    AddAMessageRow(
      message = null,
      onEvent = {},
      onNextClick = {},
      viewOnceAvailable = true,
      viewOnce = true
    )
  }
}

@DayNightPreviews
@Composable
private fun AddAMessageRowReplyPreview() {
  Previews.Preview {
    AddAMessageRow(
      message = null,
      onEvent = {},
      onNextClick = {},
      isReply = true
    )
  }
}

@DayNightPreviews
@Composable
private fun AddAMessageRowKnownRecipientPreview() {
  Previews.Preview {
    AddAMessageRow(
      message = null,
      onEvent = {},
      onNextClick = {},
      recipientChatColor = Color(0xFF3B7845)
    )
  }
}

@DayNightPreviews
@Composable
private fun AddAMessageRowDisabledPreview() {
  Previews.Preview {
    AddAMessageRow(
      message = null,
      onEvent = {},
      onNextClick = {},
      enabled = false,
      recipientChatColor = Color(0xFF3B7845)
    )
  }
}

@DayNightPreviews
@Composable
private fun AddAMessageRowLongContentPreview() {
  Previews.Preview {
    AddAMessageRow(
      message = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
      onEvent = {},
      onNextClick = {}
    )
  }
}
