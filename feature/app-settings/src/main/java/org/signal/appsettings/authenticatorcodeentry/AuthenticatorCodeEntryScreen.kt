/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.authenticatorcodeentry

import androidx.annotation.VisibleForTesting
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

@VisibleForTesting
object AuthenticatorCodeEntryTestTags {
  const val CODE_INPUT = "code-input"
  const val BUTTON_DONE = "button-done"
}

/**
 * Collects the one-time code the user's authenticator app generated, which is the last step of setting one up.
 */
@Composable
fun AuthenticatorCodeEntryScreen(
  state: AuthenticatorCodeEntryState,
  onEvent: (AuthenticatorCodeEntryEvent) -> Unit
) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  Scaffolds.Settings(
    title = stringResource(R.string.AuthenticatorCodeEntryScreen__enter_your_code),
    onNavigationClick = { onEvent(AuthenticatorCodeEntryEvent.NavigateBackClicked) },
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
        text = stringResource(R.string.AuthenticatorCodeEntryScreen__enter_the_6_digit_code),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp)
      )

      TextField(
        value = state.code,
        onValueChange = { onEvent(AuthenticatorCodeEntryEvent.CodeChanged(it)) },
        label = { Text(text = stringResource(R.string.AuthenticatorCodeEntryScreen__code)) },
        singleLine = true,
        enabled = !state.submitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onEvent(AuthenticatorCodeEntryEvent.DoneClicked) }),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .focusRequester(focusRequester)
          .testTag(AuthenticatorCodeEntryTestTags.CODE_INPUT)
      )

      Spacer(modifier = Modifier.weight(1f))

      Buttons.LargeTonal(
        onClick = { onEvent(AuthenticatorCodeEntryEvent.DoneClicked) },
        enabled = state.canSubmit,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .padding(horizontal = 24.dp, vertical = 24.dp)
          .testTag(AuthenticatorCodeEntryTestTags.BUTTON_DONE)
      ) {
        Text(text = stringResource(R.string.AuthenticatorCodeEntryScreen__done))
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun AuthenticatorCodeEntryScreenPreview() {
  Previews.Preview {
    AuthenticatorCodeEntryScreen(
      state = AuthenticatorCodeEntryState(code = "123456"),
      onEvent = {}
    )
  }
}
