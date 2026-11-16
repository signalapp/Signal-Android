/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.passkeyverification

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
import androidx.compose.foundation.layout.widthIn
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
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.test.TestTags

/**
 * Shown in place of the two-factor selection screen when the account's only second factor is a passkey, so the user
 * gets a clear instruction instead of a list with a single choice in it.
 */
@Composable
fun PasskeyVerificationScreen(
  state: PasskeyVerificationState,
  onEvent: (PasskeyVerificationScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(modifier = modifier.testTag(TestTags.PASSKEY_VERIFICATION_SCREEN)) {
    when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
      is RegistrationScaffold.Params.OnePane -> OnePaneLayout(
        params = layoutParams,
        onEvent = onEvent
      )

      is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(
        params = layoutParams,
        onEvent = onEvent
      )
    }

    if (state.showPasskeyError) {
      PasskeyErrorDialog(onEvent)
    }
  }
}

/**
 * Generic passkey failure dialog.
 */
@Composable
private fun PasskeyErrorDialog(onEvent: (PasskeyVerificationScreenEvents) -> Unit) {
  Dialogs.SimpleMessageDialog(
    message = stringResource(R.string.PasskeyVerificationScreen__something_went_wrong_with_your_passkey),
    dismiss = stringResource(android.R.string.ok),
    onDismiss = { onEvent(PasskeyVerificationScreenEvents.PasskeyErrorDismissed) },
    modifier = Modifier.testTag(TestTags.PASSKEY_VERIFICATION_ERROR_DIALOG)
  )
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  onEvent: (PasskeyVerificationScreenEvents) -> Unit
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

        Spacer(modifier = Modifier.height(16.dp))

        Description()
      }
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isElevated = scrollState.canScrollForward
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(params.footerPadding),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          ContinueButton(
            onEvent = onEvent,
            modifier = Modifier
              .widthIn(max = params.maxButtonWidth)
              .fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(16.dp))

          CancelButton(onEvent)
        }
      }
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  onEvent: (PasskeyVerificationScreenEvents) -> Unit
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
        Illustration()
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
          horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
          verticalAlignment = Alignment.CenterVertically
        ) {
          CancelButton(onEvent)

          ContinueButton(onEvent)
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
      painter = painterResource(R.drawable.image_passkey_verification),
      contentDescription = null
    )
  }
}

@Composable
private fun Description(twoPane: Boolean = false) {
  Text(
    text = stringResource(R.string.PasskeyVerificationScreen__two_factor_authentication),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.PasskeyVerificationScreen__to_continue_verify_your_account),
    style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp)
  )
}

@Composable
private fun ContinueButton(
  onEvent: (PasskeyVerificationScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Buttons.LargeTonal(
    onClick = { onEvent(PasskeyVerificationScreenEvents.ContinueClicked) },
    modifier = modifier.testTag(TestTags.PASSKEY_VERIFICATION_CONTINUE_BUTTON)
  ) {
    Text(text = stringResource(R.string.PasskeyVerificationScreen__continue))
  }
}

@Composable
private fun CancelButton(onEvent: (PasskeyVerificationScreenEvents) -> Unit) {
  TextButton(
    onClick = { onEvent(PasskeyVerificationScreenEvents.CancelClicked) },
    modifier = Modifier.testTag(TestTags.PASSKEY_VERIFICATION_CANCEL_BUTTON)
  ) {
    Text(
      text = stringResource(R.string.PasskeyVerificationScreen__cancel),
      color = MaterialTheme.colorScheme.primary,
      style = MaterialTheme.typography.labelLarge
    )
  }
}

@AllDevicePreviews
@Composable
private fun PasskeyVerificationScreenPreview() {
  Previews.Preview {
    PasskeyVerificationScreen(
      state = PasskeyVerificationState(),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PasskeyVerificationScreenErrorPreview() {
  Previews.Preview {
    PasskeyVerificationScreen(
      state = PasskeyVerificationState(showPasskeyError = true),
      onEvent = {}
    )
  }
}
