/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.test.TestTags
import org.signal.signallogin.card.SignalLoginCard
import java.util.UUID

/**
 * Presents the Signal Login the user just purchased and prompts them to save it, either into the system password
 * manager or by recording it themselves.
 */
@Composable
fun SignalLoginInfoScreen(
  state: SignalLoginInfoState,
  onEvent: (SignalLoginInfoScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val simpleError: Pair<String, SignalLoginInfoScreenEvents>? = when {
    state.dialogs.saveFailed -> stringResource(R.string.SignalLoginInfoScreen__your_signal_login_could_not_be_saved) to SignalLoginInfoScreenEvents.SaveFailedDialogDismissed
    state.dialogs.unknownError -> stringResource(R.string.VerificationCodeScreen__an_unexpected_error_occurred) to SignalLoginInfoScreenEvents.UnknownErrorDialogDismissed
    else -> null
  }

  simpleError?.let { (message, dismissedEvent) ->
    Dialogs.SimpleMessageDialog(
      message = message,
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(dismissedEvent) }
    )
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.SIGNAL_LOGIN_INFO_SCREEN)
  ) {
    when (val params = RegistrationScaffold.rememberLayoutParams()) {
      is RegistrationScaffold.Params.OnePane -> OnePaneLayout(params, state, onEvent)
      is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(params, state, onEvent)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: SignalLoginInfoState,
  onEvent: (SignalLoginInfoScreenEvents) -> Unit
) {
  val scrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  OnePaneRegistrationScaffold(
    params = params,
    topBar = { TopBar(scrollBehavior = topBarScrollBehavior) },
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        Header()

        Spacer(modifier = Modifier.height(32.dp))

        CredentialCard(
          state = state,
          onEvent = onEvent,
          modifier = Modifier.align(Alignment.CenterHorizontally)
        )
      }
    },
    footer = { Footer(params, state, scrollState.canScrollForward, onEvent) }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: SignalLoginInfoState,
  onEvent: (SignalLoginInfoScreenEvents) -> Unit
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  TwoPaneRegistrationScaffold(
    params = params,
    topBar = { TopBar(scrollBehavior = topBarScrollBehavior) },
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        Header(twoPane = true)
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues),
        verticalArrangement = Arrangement.Center
      ) {
        CredentialCard(
          state = state,
          onEvent = onEvent,
          modifier = Modifier.align(Alignment.CenterHorizontally)
        )
      }
    },
    footer = { Footer(params, state, firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward, onEvent) }
  )
}

/**
 * Title-less top app bar with no navigation icon: registration is already complete at this point, so there is nothing
 * to go back to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(scrollBehavior: TopAppBarScrollBehavior) {
  Scaffolds.DefaultTopAppBar(
    title = "",
    titleContent = { _, _ -> },
    onNavigationClick = { },
    navigationIcon = null,
    scrollBehavior = scrollBehavior
  )
}

@Composable
private fun ColumnScope.Header(twoPane: Boolean = false) {
  Image(
    painter = painterResource(R.drawable.image_signal_login_keys_checkmark),
    contentDescription = null,
    modifier = Modifier
      .padding(bottom = 24.dp)
      .align(Alignment.CenterHorizontally)
      .size(96.dp)
  )

  Text(
    text = stringResource(R.string.SignalLoginInfoScreen__your_signal_login),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.SignalLoginInfoScreen__thanks_for_your_purchase),
    style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp)
  )
}

@Composable
private fun CredentialCard(
  state: SignalLoginInfoState,
  onEvent: (SignalLoginInfoScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  if (state.aci != null && state.aep != null) {
    SignalLoginCard(
      aci = state.aci,
      aep = state.aep,
      onViewDetailsClicked = { onEvent(SignalLoginInfoScreenEvents.ViewDetailsClicked) },
      modifier = modifier.testTag(TestTags.SIGNAL_LOGIN_INFO_CREDENTIAL_CARD)
    )
  }
}

@Composable
private fun Footer(
  params: RegistrationScaffold.Params,
  state: SignalLoginInfoState,
  isElevated: Boolean,
  onEvent: (SignalLoginInfoScreenEvents) -> Unit
) {
  RegistrationScaffold.FooterSurface(isElevated = isElevated) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(params.footerPadding)
    ) {
      if (state.isPasswordManagerAvailable) {
        Buttons.LargeTonal(
          onClick = { onEvent(SignalLoginInfoScreenEvents.SaveToPasswordManagerClicked) },
          enabled = !state.showSpinner,
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          ),
          modifier = Modifier
            .widthIn(max = params.maxButtonWidth)
            .fillMaxWidth()
            .testTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_TO_PASSWORD_MANAGER_BUTTON)
        ) {
          if (state.showSpinner) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp)
            )
          } else {
            Text(stringResource(R.string.SignalLoginInfoScreen__save_to_password_manager))
          }
        }
      }

      Buttons.LargeTonal(
        onClick = { onEvent(SignalLoginInfoScreenEvents.SaveManuallyClicked) },
        enabled = !state.showSpinner,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = SignalTheme.colors.colorSurface2,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .widthIn(max = params.maxButtonWidth)
          .fillMaxWidth()
          .testTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_MANUALLY_BUTTON)
      ) {
        Text(stringResource(R.string.SignalLoginInfoScreen__save_manually))
      }
    }
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginInfoScreenPreview() {
  Previews.Preview {
    SignalLoginInfoScreen(
      state = SignalLoginInfoState(
        aci = ServiceId.ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91")),
        aep = AccountEntropyPool("uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"),
        isPasswordManagerAvailable = true
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginInfoScreenNoPasswordManagerPreview() {
  Previews.Preview {
    SignalLoginInfoScreen(
      state = SignalLoginInfoState(
        aci = ServiceId.ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91")),
        aep = AccountEntropyPool("uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t")
      ),
      onEvent = {}
    )
  }
}
