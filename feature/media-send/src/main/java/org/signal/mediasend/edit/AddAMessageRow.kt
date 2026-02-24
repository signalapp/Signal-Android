/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.util.isNotNullOrBlank

@Composable
internal fun AddAMessageRow(
  message: String?,
  callback: AddAMessageRowCallback,
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
    ) {
      IconButtons.IconButton(
        onClick = callback::onEmojiKeyboardClick
      ) {
        Icon(
          painter = SignalIcons.Emoji.painter,
          contentDescription = "Open emoji keyboard"
        )
      }

      Crossfade(
        targetState = message.isNotNullOrBlank(),
        modifier = Modifier.weight(1f)
      ) { isNotEmpty ->
        if (isNotEmpty) {
          BasicTextField(
            value = message ?: "",
            onValueChange = callback::onMessageChange
          )
        } else
          Text(
            text = "Message",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
      }
    }

    IconButtons.IconButton(
      onClick = callback::onNextClick,
      modifier = Modifier
        .padding(start = 12.dp)
        .background(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = CircleShape
        )
    ) {
      Icon(
        painter = SignalIcons.ArrowEnd.painter,
        contentDescription = "Open emoji keyboard",
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
      callback = AddAMessageRowCallback.Empty
    )
  }
}

internal interface AddAMessageRowCallback {
  fun onMessageChange(message: String)
  fun onEmojiKeyboardClick()
  fun onNextClick()

  object Empty : AddAMessageRowCallback {
    override fun onMessageChange(message: String) = Unit
    override fun onEmojiKeyboardClick() = Unit
    override fun onNextClick() = Unit
  }
}
