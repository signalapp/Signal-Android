/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpcodeentry

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.uicomponents.codeentryfield.CodeEntryField
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState

@VisibleForTesting
object TotpCodeEntryTestTags {
  const val BUTTON_NEXT = "button-next"
  const val ERROR = "error"
}

/**
 * Collects the one-time code the user's authenticator app generated, which is the last step of setting one up.
 */
@Composable
fun TotpCodeEntryScreen(
  state: TotpCodeEntryState,
  onEvent: (TotpCodeEntryEvent) -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(R.string.TotpCodeEntryScreen__enter_your_code),
    onNavigationClick = { onEvent(TotpCodeEntryEvent.NavigateBackClicked) },
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
        text = stringResource(R.string.TotpCodeEntryScreen__enter_the_6_digit_code),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 24.dp, end = 24.dp, top = 12.dp)
      )

      Spacer(modifier = Modifier.height(24.dp))

      val errorMessage = state.error.message()

      CodeEntryField(
        state = state.codeEntry,
        onEvent = { onEvent(TotpCodeEntryEvent.CodeEntryEvent(it)) },
        enabled = !state.submitting,
        isError = errorMessage != null,
        modifier = Modifier.padding(horizontal = 24.dp)
      )

      if (errorMessage != null) {
        Text(
          text = errorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag(TotpCodeEntryTestTags.ERROR)
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      Buttons.LargeTonal(
        onClick = { onEvent(TotpCodeEntryEvent.NextClicked) },
        enabled = state.canSubmit,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .padding(horizontal = 24.dp, vertical = 24.dp)
          .testTag(TotpCodeEntryTestTags.BUTTON_NEXT)
      ) {
        Text(text = stringResource(R.string.TotpCodeEntryScreen__next))
      }
    }
  }
}

/**
 * The message shown under the code field, or null when there's nothing wrong.
 */
@Composable
private fun TotpCodeEntryState.Error.message(): String? = when (this) {
  TotpCodeEntryState.Error.None -> null
  TotpCodeEntryState.Error.IncorrectCode -> stringResource(R.string.TotpCodeEntryScreen__incorrect_code)
  TotpCodeEntryState.Error.NetworkFailure -> stringResource(R.string.TotpCodeEntryScreen__couldnt_reach_signal)
}

@DayNightPreviews
@Composable
private fun TotpCodeEntryScreenPreview() {
  Previews.Preview {
    TotpCodeEntryScreen(
      state = TotpCodeEntryState(codeEntry = CodeEntryFieldState(digits = listOf("1", "2", "3", "4", "5", "6"))),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun TotpCodeEntryScreenErrorPreview() {
  Previews.Preview {
    TotpCodeEntryScreen(
      state = TotpCodeEntryState(
        codeEntry = CodeEntryFieldState(digits = listOf("1", "2", "3", "4", "5", "6")),
        error = TotpCodeEntryState.Error.IncorrectCode
      ),
      onEvent = {}
    )
  }
}
