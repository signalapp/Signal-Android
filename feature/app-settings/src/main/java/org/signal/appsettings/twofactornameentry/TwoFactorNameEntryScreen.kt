/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactornameentry

import androidx.annotation.StringRes
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
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.TextFields

@VisibleForTesting
object TwoFactorNameEntryTestTags {
  const val NAME_INPUT = "name-input"
  const val BUTTON_NEXT = "button-next"
}

/**
 * Collects the name the user wants to identify a second factor by, either right after adding one or when renaming one
 * that already exists.
 */
@Composable
fun TwoFactorNameEntryScreen(
  state: TwoFactorNameEntryState,
  onEvent: (TwoFactorNameEntryEvent) -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  val interactionSource = remember { MutableInteractionSource() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  Scaffolds.Settings(
    title = stringResource(R.string.TwoFactorNameEntryScreen__choose_a_name),
    onNavigationClick = { onEvent(TwoFactorNameEntryEvent.NavigateBackClicked) },
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
        text = stringResource(instructionsFor(state.kind, state.renaming)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp)
      )

      TextField(
        value = state.name,
        onValueChange = { onEvent(TwoFactorNameEntryEvent.NameChanged(it)) },
        label = { TextFields.Label(stringResource(R.string.TwoFactorNameEntryScreen__name), state.name.isNotEmpty(), interactionSource) },
        interactionSource = interactionSource,
        singleLine = true,
        enabled = !state.submitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onEvent(TwoFactorNameEntryEvent.NextClicked) }),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .focusRequester(focusRequester)
          .testTag(TwoFactorNameEntryTestTags.NAME_INPUT)
      )

      Spacer(modifier = Modifier.weight(1f))

      Buttons.LargeTonal(
        onClick = { onEvent(TwoFactorNameEntryEvent.NextClicked) },
        enabled = state.canSubmit,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .padding(horizontal = 24.dp, vertical = 24.dp)
          .testTag(TwoFactorNameEntryTestTags.BUTTON_NEXT)
      ) {
        Text(text = stringResource(R.string.TwoFactorNameEntryScreen__next))
      }
    }
  }
}

/** The copy below the field is the only thing that changes between the kinds, and between naming and renaming. */
@StringRes
private fun instructionsFor(kind: TwoFactorMethod.Kind, renaming: Boolean): Int {
  return when (kind) {
    TwoFactorMethod.Kind.AUTHENTICATOR_APP -> if (renaming) {
      R.string.TwoFactorNameEntryScreen__choose_a_unique_name
    } else {
      R.string.TwoFactorNameEntryScreen__choose_a_unique_name_to_help_you_identify_it
    }
    TwoFactorMethod.Kind.PASSKEY -> if (renaming) {
      R.string.TwoFactorNameEntryScreen__choose_a_unique_name_for_this_passkey
    } else {
      R.string.TwoFactorNameEntryScreen__choose_a_unique_name_for_this_passkey_to_help_you_identify_it
    }
    TwoFactorMethod.Kind.OTHER -> if (renaming) {
      R.string.TwoFactorNameEntryScreen__choose_a_unique_name_for_this_method
    } else {
      R.string.TwoFactorNameEntryScreen__choose_a_unique_name_for_this_method_to_help_you_identify_it
    }
  }
}

@DayNightPreviews
@Composable
private fun TwoFactorNameEntryScreenPreview() {
  Previews.Preview {
    TwoFactorNameEntryScreen(
      state = TwoFactorNameEntryState(name = "Bitwarden Authenticator"),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun TwoFactorNameEntryScreenRenamePreview() {
  Previews.Preview {
    TwoFactorNameEntryScreen(
      state = TwoFactorNameEntryState(name = "Twilio Authy", renaming = true),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun TwoFactorNameEntryScreenPasskeyPreview() {
  Previews.Preview {
    TwoFactorNameEntryScreen(
      state = TwoFactorNameEntryState(name = "Pixel Phone", kind = TwoFactorMethod.Kind.PASSKEY),
      onEvent = {}
    )
  }
}
