/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginmanualsave

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.screens.shared.BackTopAppBar
import org.signal.registration.test.TestTags
import org.signal.signallogin.details.SignalLoginKeyDetails

/**
 * Spells out both halves of the Signal Login the user just bought so they can record it somewhere themselves, then
 * sends them on to a screen that confirms they did.
 */
@Composable
fun SignalLoginViewDetailsForManualSaveScreen(
  state: SignalLoginViewDetailsForManualSaveState,
  onEvent: (SignalLoginViewDetailsForManualSaveScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val params = RegistrationScaffold.rememberLayoutParams()

  if (state.showConfirmSavedSheet) {
    ConfirmLoginSavedBottomSheet(
      maxButtonWidth = params.maxButtonWidth,
      onContinue = { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.ConfirmSavedContinueClicked) },
      onShowLoginInfoAgain = { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.ShowLoginInfoAgainClicked) },
      onDismiss = { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.ConfirmSavedSheetDismissed) }
    )
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_SCREEN)
  ) {
    when (params) {
      is RegistrationScaffold.Params.OnePane -> OnePaneLayout(params, state, onEvent)
      is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(params, state, onEvent)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: SignalLoginViewDetailsForManualSaveState,
  onEvent: (SignalLoginViewDetailsForManualSaveScreenEvents) -> Unit
) {
  val scrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  OnePaneRegistrationScaffold(
    params = params,
    topBar = { BackTopAppBar(scrollBehavior = topBarScrollBehavior, onBackClick = { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.BackClicked) }) },
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(scrollState)
          .padding(paddingValues.vertical())
      ) {
        Header(modifier = Modifier.padding(paddingValues.horizontal()))

        Spacer(modifier = Modifier.height(16.dp))

        KeyDetails(state = state, onEvent = onEvent)
      }
    },
    footer = { Footer(params, scrollState.canScrollForward, onEvent) }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: SignalLoginViewDetailsForManualSaveState,
  onEvent: (SignalLoginViewDetailsForManualSaveScreenEvents) -> Unit
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  TwoPaneRegistrationScaffold(
    params = params,
    topBar = { BackTopAppBar(scrollBehavior = topBarScrollBehavior, onBackClick = { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.BackClicked) }) },
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
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues.vertical())
      ) {
        KeyDetails(state = state, onEvent = onEvent)
      }
    },
    footer = { Footer(params, firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward, onEvent) }
  )
}

@Composable
private fun Header(modifier: Modifier = Modifier, twoPane: Boolean = false) {
  Column(modifier = modifier) {
    Image(
      painter = painterResource(R.drawable.image_signal_login_lock),
      contentDescription = null,
      modifier = Modifier
        .padding(bottom = 24.dp)
        .align(Alignment.CenterHorizontally)
        .size(96.dp)
    )

    Text(
      text = stringResource(R.string.SignalLoginViewDetailsForManualSaveScreen__save_your_signal_login),
      style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
      modifier = Modifier
        .fillMaxWidth()
        .attachDebugLogHelper()
    )

    Text(
      text = stringResource(R.string.SignalLoginViewDetailsForManualSaveScreen__store_your_signal_login_somewhere_safe),
      style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 16.dp)
    )
  }
}

@Composable
private fun KeyDetails(
  state: SignalLoginViewDetailsForManualSaveState,
  onEvent: (SignalLoginViewDetailsForManualSaveScreenEvents) -> Unit
) {
  SignalLoginKeyDetails(
    accountId = state.accountId,
    recoveryKeyGroups = state.recoveryKeyGroups,
    onCopyAccountId = { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.CopyAccountIdClicked(it)) },
    onCopyRecoveryKey = { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.CopyRecoveryKeyClicked(it)) }
  )
}

@Composable
private fun Footer(
  params: RegistrationScaffold.Params,
  isElevated: Boolean,
  onEvent: (SignalLoginViewDetailsForManualSaveScreenEvents) -> Unit
) {
  RegistrationScaffold.FooterSurface(isElevated = isElevated) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(params.footerPadding)
    ) {
      Buttons.LargeTonal(
        onClick = { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.SaveAsPdfClicked) },
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .widthIn(max = params.maxButtonWidth)
          .fillMaxWidth()
          .testTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_SAVE_AS_PDF_BUTTON)
      ) {
        Text(stringResource(R.string.SignalLoginViewDetailsForManualSaveScreen__save_as_pdf))
      }

      Buttons.LargeTonal(
        onClick = { onEvent(SignalLoginViewDetailsForManualSaveScreenEvents.ContinueClicked) },
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .widthIn(max = params.maxButtonWidth)
          .fillMaxWidth()
          .testTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_CONTINUE_BUTTON)
      ) {
        Text(stringResource(R.string.SignalLoginViewDetailsForManualSaveScreen__continue))
      }
    }
  }
}

/** The vertical half of a scaffold's pane padding, for content that supplies its own horizontal inset. */
private fun PaddingValues.vertical(): PaddingValues {
  return PaddingValues(top = calculateTopPadding(), bottom = calculateBottomPadding())
}

/** The horizontal half of a scaffold's pane padding, to hand to the content that [vertical] left un-inset. */
@Composable
private fun PaddingValues.horizontal(): PaddingValues {
  val layoutDirection = LocalLayoutDirection.current
  return PaddingValues(start = calculateStartPadding(layoutDirection), end = calculateEndPadding(layoutDirection))
}

@AllDevicePreviews
@Composable
private fun SignalLoginViewDetailsForManualSaveScreenPreview() {
  Previews.Preview {
    SignalLoginViewDetailsForManualSaveScreen(
      state = SignalLoginViewDetailsForManualSaveState(
        accountId = "A6B28482-2E32-83D0-7F23-91360A4C2B91",
        recoveryKey = "UY38JH2778HJJHJ8LK19GA61S672JSJ=89R=23S6A578=9BAP92J2YH5T326VV7T"
      ),
      onEvent = {}
    )
  }
}
