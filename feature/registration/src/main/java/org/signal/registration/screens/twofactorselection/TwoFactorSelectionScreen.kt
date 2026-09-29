/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.twofactorselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.test.TestTags

/**
 * Two-factor authentication method selection screen. Lets the user pick how they want to verify their account when the
 * account has more than one second factor registered.
 */
@Composable
fun TwoFactorSelectionScreen(
  state: TwoFactorSelectionState,
  onEvent: (TwoFactorSelectionScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(modifier = modifier.testTag(TestTags.TWO_FACTOR_SELECTION_SCREEN)) {
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
  state: TwoFactorSelectionState,
  onEvent: (TwoFactorSelectionScreenEvents) -> Unit
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
        Description()

        Spacer(modifier = Modifier.height(40.dp))

        MethodCards(state = state, onEvent = onEvent)
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
  state: TwoFactorSelectionState,
  onEvent: (TwoFactorSelectionScreenEvents) -> Unit
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
        MethodCards(state = state, onEvent = onEvent)
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
private fun Description(twoPane: Boolean = false) {
  Text(
    text = stringResource(R.string.TwoFactorSelectionScreen__two_factor_authentication),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.TwoFactorSelectionScreen__choose_a_method_below),
    style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp)
  )
}

@Composable
private fun MethodCards(
  state: TwoFactorSelectionState,
  onEvent: (TwoFactorSelectionScreenEvents) -> Unit
) {
  state.methods.forEachIndexed { index, method ->
    if (index > 0) {
      Spacer(modifier = Modifier.height(12.dp))
    }
    MethodCard(
      method = method,
      onClick = { onEvent(TwoFactorSelectionScreenEvents.MethodSelected(method)) }
    )
  }
}

@Composable
private fun MethodCard(
  method: TwoFactorMethod,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  when (method) {
    is TwoFactorMethod.Passkey -> {
      SelectionCard(
        imageVector = SignalIcons.Key.imageVector,
        title = stringResource(R.string.TwoFactorSelectionScreen__passkey),
        subtitle = stringResource(R.string.TwoFactorSelectionScreen__tap_to_use_your_device_biometrics),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.TWO_FACTOR_SELECTION_PASSKEY_OPTION)
      )
    }

    TwoFactorMethod.AuthenticatorApp -> {
      SelectionCard(
        imageVector = SignalIcons.DevicePhone.imageVector,
        title = stringResource(R.string.TwoFactorSelectionScreen__authenticator_app),
        subtitle = stringResource(R.string.TwoFactorSelectionScreen__enter_a_one_time_code),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.TWO_FACTOR_SELECTION_AUTHENTICATOR_APP_OPTION)
      )
    }
  }
}

@Composable
private fun SelectionCard(
  imageVector: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(16.dp)
    ) {
      Icon(imageVector = imageVector, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))

      Spacer(modifier = Modifier.width(16.dp))

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun CancelButton(onEvent: (TwoFactorSelectionScreenEvents) -> Unit) {
  TextButton(
    onClick = { onEvent(TwoFactorSelectionScreenEvents.CancelClicked) },
    modifier = Modifier.testTag(TestTags.TWO_FACTOR_SELECTION_CANCEL_BUTTON)
  ) {
    Text(
      text = stringResource(R.string.TwoFactorSelectionScreen__cancel),
      color = MaterialTheme.colorScheme.primary,
      style = MaterialTheme.typography.labelLarge
    )
  }
}

private val PREVIEW_WEB_AUTHN_PARAMETERS = WebAuthnParameters(challenge = byteArrayOf(1, 2, 3), timeoutSeconds = 60, allowedCredentialIds = listOf(byteArrayOf(4, 5, 6)))

@AllDevicePreviews
@Composable
private fun TwoFactorSelectionScreenPreview() {
  Previews.Preview {
    TwoFactorSelectionScreen(
      state = TwoFactorSelectionState(
        methods = listOf(TwoFactorMethod.Passkey(PREVIEW_WEB_AUTHN_PARAMETERS), TwoFactorMethod.AuthenticatorApp)
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun TwoFactorSelectionScreenAuthenticatorAppOnlyPreview() {
  Previews.Preview {
    TwoFactorSelectionScreen(
      state = TwoFactorSelectionState(methods = listOf(TwoFactorMethod.AuthenticatorApp)),
      onEvent = {}
    )
  }
}
