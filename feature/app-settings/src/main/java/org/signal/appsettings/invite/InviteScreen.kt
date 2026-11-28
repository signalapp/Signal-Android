/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.invite

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object InviteTestTags {
  const val SCROLLER = "scroller"
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

  Scaffolds.Settings(
    title = stringResource(id = R.string.InviteScreen__invite_friends),
    onNavigationClick = { onEvent(InviteEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(id = CoreUiR.string.CloseButton__content_description),
    modifier = modifier,
    expandableTitle = true
  ) { paddingValues ->
    AppSettingsLazyColumn(
      scaffoldPadding = paddingValues,
      modifier = Modifier.testTag(InviteTestTags.SCROLLER)
    ) {
      item {
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
            .horizontalGutters()
            .padding(vertical = 16.dp)
            .testTag(InviteTestTags.INPUT_INVITE_TEXT),
          shape = RoundedCornerShape(12.dp)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(id = R.string.InviteScreen__share),
          icon = SignalIcons.Share.imageVector,
          onClick = { onEvent(InviteEvent.ShareClicked) },
          modifier = Modifier.testTag(InviteTestTags.ROW_SHARE)
        )
      }
    }
  }
}

@AllDevicePreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun InviteScreenPreview() {
  InviteScreen(
    state = InviteState(inviteText = "Join me on Signal!"),
    onEvent = {}
  )
}
