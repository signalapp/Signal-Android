/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpnameentry

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.TextFields

@VisibleForTesting
object TotpNameEntryTestTags {
  const val NAME_INPUT = "name-input"
  const val BUTTON_NEXT = "button-next"
}

/**
 * Collects the name the user wants to identify an authenticator app by, either right after pairing one or when
 * renaming one that already exists.
 */
@Composable
fun TotpNameEntryScreen(
  state: TotpNameEntryState,
  onEvent: (TotpNameEntryEvent) -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  val interactionSource = remember { MutableInteractionSource() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  Scaffolds.Settings(
    title = stringResource(R.string.TotpNameEntryScreen__choose_a_name),
    onNavigationClick = { onEvent(TotpNameEntryEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .imePadding(),
      horizontalAlignment = Alignment.End
    ) {
      Text(
        text = if (state.renaming) {
          stringResource(R.string.TotpNameEntryScreen__choose_a_unique_name)
        } else {
          stringResource(R.string.TotpNameEntryScreen__choose_a_unique_name_to_help_you_identify_it)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp)
      )

      TextField(
        value = state.name,
        onValueChange = { onEvent(TotpNameEntryEvent.NameChanged(it)) },
        label = { TextFields.Label(stringResource(R.string.TotpNameEntryScreen__name), state.name.isNotEmpty(), interactionSource) },
        interactionSource = interactionSource,
        singleLine = true,
        enabled = !state.submitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onEvent(TotpNameEntryEvent.NextClicked) }),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .focusRequester(focusRequester)
          .testTag(TotpNameEntryTestTags.NAME_INPUT)
      )

      Spacer(modifier = Modifier.weight(1f))

      Buttons.LargeTonal(
        onClick = { onEvent(TotpNameEntryEvent.NextClicked) },
        enabled = state.canSubmit,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .padding(horizontal = 24.dp, vertical = 24.dp)
          .testTag(TotpNameEntryTestTags.BUTTON_NEXT)
      ) {
        Text(text = stringResource(R.string.TotpNameEntryScreen__next))
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun TotpNameEntryScreenPreview() {
  Previews.Preview {
    TotpNameEntryScreen(
      state = TotpNameEntryState(name = "Bitwarden Authenticator"),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun TotpNameEntryScreenRenamePreview() {
  Previews.Preview {
    TotpNameEntryScreen(
      state = TotpNameEntryState(name = "Twilio Authy", renaming = true),
      onEvent = {}
    )
  }
}
