/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.invite

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons

@VisibleForTesting
object InviteTestTags {
  const val INPUT_INVITE_TEXT = "input-invite-text"
  const val ROW_SHARE = "row-share"
}

/**
 * Screen to invite a user to the app.
 */
@Composable
fun InviteScreen(
  state: InviteState,
  onEvent: (InviteEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  var inviteTextFieldValue by remember { mutableStateOf(TextFieldValue(text = state.inviteText, selection = TextRange(state.inviteText.length))) }

  Column(
    modifier = modifier
      .padding(16.dp)
      .fillMaxHeight()
  ) {
    TextField(
      value = inviteTextFieldValue,
      onValueChange = { newValue ->
        inviteTextFieldValue = newValue
        onEvent(InviteEvent.InviteTextChanged(newValue.text))
      },
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp)
        .testTag(InviteTestTags.INPUT_INVITE_TEXT),
      shape = RoundedCornerShape(12.dp)
    )

    Row(
      modifier = Modifier
        .clickable(onClick = { onEvent(InviteEvent.ShareClicked) })
        .fillMaxWidth()
        .padding(vertical = 16.dp)
        .testTag(InviteTestTags.ROW_SHARE)
    ) {
      Icon(
        imageVector = SignalIcons.Share.imageVector,
        contentDescription = stringResource(R.string.InviteScreen__share)
      )

      Text(
        text = stringResource(id = R.string.InviteScreen__share),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp)
      )
    }
  }
}

@AllDevicePreviews
@Composable
private fun InviteScreenPreview() {
  Previews.Preview {
    InviteScreen(
      state = InviteState(inviteText = "Join me on Signal!"),
      onEvent = {}
    )
  }
}
