/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.mediasend.R

/**
 * Because we need to be able to support stuff like mentions, styled text, and custom emoji, we need to allow
 * the users of this feature to inject their own text-field.
 */
val LocalAddAMessageRowTextField = compositionLocalOf<@Composable (String, Modifier) -> Unit> {
  { message, modifier ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = modifier
    )
  }
}

@Composable
fun AddAMessageRow(
  message: String?,
  onEvent: (MediaEditScreenEvent) -> Unit,
  onNextClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
        .weight(1f)
        .heightIn(min = 40.dp)
        .clickable(onClickLabel = stringResource(R.string.AddAMessageRow__add_a_message), onClick = { onEvent(MediaEditScreenEvent.AddMessageClick()) }, role = Role.Button)
    ) {
      IconButtons.IconButton(
        onClick = { onEvent(MediaEditScreenEvent.AddMessageClick(startWithEmojiKeyboard = true)) }
      ) {
        Icon(
          painter = SignalIcons.Emoji.painter,
          contentDescription = stringResource(R.string.AddAMessageRow__open_emoji_keyboard)
        )
      }

      LocalAddAMessageRowTextField.current(
        message ?: stringResource(R.string.AddAMessageRow__message),
        Modifier
          .weight(1f)
          .padding(end = 16.dp)
      )
    }

    IconButtons.IconButton(
      onClick = onNextClick,
      modifier = Modifier
        .padding(start = 12.dp)
        .background(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = CircleShape
        )
    ) {
      Icon(
        painter = SignalIcons.ArrowEnd.painter,
        contentDescription = stringResource(R.string.AddAMessageRow__next),
        modifier = Modifier
          .size(40.dp)
          .padding(8.dp)
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
