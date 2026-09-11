/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.totpentry

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.test.TestTags
import org.signal.uicomponents.codeentryfield.CodeEntryField
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState

/**
 * Two-factor authentication code entry screen. Displays a 6-digit code input in XXX-XXX format for a code from the
 * user's authenticator app.
 */
@Composable
fun TotpEntryScreen(
  state: TotpEntryState,
  onEvent: (TotpEntryScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(modifier = modifier.testTag(TestTags.TOTP_ENTRY_SCREEN)) {
    when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
      is RegistrationScaffold.Params.OnePane -> OnePaneLayout(
        params = layoutParams,
        state = state,
        onEvent = onEvent
      )

      is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(
        params = layoutParams,
        state = state,
        onEvent = onEvent
      )
    }
  }
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: TotpEntryState,
  onEvent: (TotpEntryScreenEvents) -> Unit
) {
  val scrollState = rememberScrollState()

  OnePaneRegistrationScaffold(
    modifier = Modifier.fillMaxSize(),
    params = params,
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        Illustration()

        Spacer(modifier = Modifier.height(32.dp))

        Description()

        Spacer(modifier = Modifier.height(32.dp))

        CodeEntryField(
          state = state.codeEntry,
          onEvent = { onEvent(TotpEntryScreenEvents.CodeEntryEvent(it)) }
        )
      }
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isElevated = scrollState.canScrollForward
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(params.footerPadding),
          horizontalArrangement = Arrangement.Center
        ) {
          CancelButton(onEvent)
        }
      }
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: TotpEntryState,
  onEvent: (TotpEntryScreenEvents) -> Unit
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    modifier = Modifier.fillMaxSize(),
    params = params,
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        Illustration()

        Spacer(modifier = Modifier.height(32.dp))

        Description(twoPane = true)
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      ) {
        CodeEntryField(
          state = state.codeEntry,
          onEvent = { onEvent(TotpEntryScreenEvents.CodeEntryEvent(it)) }
        )
      }
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isElevated = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(params.footerPadding),
          horizontalArrangement = Arrangement.End
        ) {
          CancelButton(onEvent)
        }
      }
    }
  )
}

@Composable
private fun Illustration() {
  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center
  ) {
    Image(
      painter = painterResource(R.drawable.image_totp_phone),
      contentDescription = null
    )
  }
}

@Composable
private fun Description(twoPane: Boolean = false) {
  Text(
    text = stringResource(R.string.TotpEntryScreen__two_factor_authentication),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.TotpEntryScreen__to_continue_enter_the_code),
    style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp)
  )
}

@Composable
private fun CancelButton(onEvent: (TotpEntryScreenEvents) -> Unit) {
  TextButton(
    onClick = { onEvent(TotpEntryScreenEvents.CancelClicked) },
    modifier = Modifier.testTag(TestTags.TOTP_ENTRY_CANCEL_BUTTON)
  ) {
    Text(
      text = stringResource(R.string.TotpEntryScreen__cancel),
      color = MaterialTheme.colorScheme.primary,
      style = MaterialTheme.typography.labelLarge
    )
  }
}

@AllDevicePreviews
@Composable
private fun TotpEntryScreenPreview() {
  Previews.Preview {
    TotpEntryScreen(
      state = TotpEntryState(),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun TotpEntryScreenPartiallyFilledPreview() {
  Previews.Preview {
    TotpEntryScreen(
      state = TotpEntryState(
        codeEntry = CodeEntryFieldState(
          digits = listOf("4", "1", "8", "3", "7", ""),
          focusedDigitIndex = 5
        )
      ),
      onEvent = {}
    )
  }
}
