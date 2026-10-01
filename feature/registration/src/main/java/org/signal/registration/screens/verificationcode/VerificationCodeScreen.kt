/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.network.api.RegistrationApiV2.VerificationCodeTransport
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.screens.shared.ContactSupportDialog
import org.signal.registration.test.TestTags
import org.signal.uicomponents.codeentryfield.CodeEntryField
import kotlin.time.Duration.Companion.seconds

/**
 * Verification code entry screen for the registration flow.
 * Displays a 6-digit code input in XXX-XXX format with countdown buttons
 * for resend SMS and call me actions.
 */
@Composable
fun VerificationCodeScreen(
  state: VerificationCodeState,
  onEvent: (VerificationCodeScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val resources = LocalResources.current

  LaunchedEffect(state.rateLimits) {
    if (state.smsResendCountdown() != null || state.callRequestCountdown() != null) {
      while (true) {
        delay(1000)
        onEvent(VerificationCodeScreenEvents.CountdownTick)
      }
    }
  }

  LaunchedEffect(state.snackbars) {
    val (message, dismissedEvent) = when {
      state.snackbars.incorrectVerificationCode -> resources.getString(R.string.VerificationCodeScreen__incorrect_code) to VerificationCodeScreenEvents.IncorrectVerificationCodeSnackbarDismissed
      state.snackbars.networkError -> resources.getString(R.string.VerificationCodeScreen__network_error) to VerificationCodeScreenEvents.NetworkErrorSnackbarDismissed
      state.snackbars.rateLimitedRetryAfter != null -> resources.getString(R.string.VerificationCodeScreen__too_many_attempts_try_again_in_s, state.snackbars.rateLimitedRetryAfter.toString()) to VerificationCodeScreenEvents.RateLimitedSnackbarDismissed
      state.snackbars.unknownError -> resources.getString(R.string.VerificationCodeScreen__an_unexpected_error_occurred) to VerificationCodeScreenEvents.UnknownErrorSnackbarDismissed
      state.snackbars.registrationError -> resources.getString(R.string.VerificationCodeScreen__registration_error) to VerificationCodeScreenEvents.RegistrationErrorSnackbarDismissed
      else -> return@LaunchedEffect
    }

    snackbarHostState.showSnackbar(message)
    onEvent(dismissedEvent)
  }

  RequestCodeErrorDialogs(state.dialogs, onEvent)

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    onEvent(VerificationCodeScreenEvents.Foregrounded)
  }

  if (state.showContactSupportSheet) {
    ContactSupportBottomSheet(
      onContactSupport = { onEvent(VerificationCodeScreenEvents.ContactSupportDialog) },
      onDismiss = { onEvent(VerificationCodeScreenEvents.DismissContactSupport) }
    )
  }

  if (state.showContactSupportDialog) {
    ContactSupportDialog(
      subject = R.string.VerificationCodeScreen__contact_support_email_subject,
      filter = R.string.VerificationCodeScreen__contact_support_email_filter,
      onDismiss = { onEvent(VerificationCodeScreenEvents.DismissContactSupportDialog) }
    )
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    modifier = modifier
  ) { innerPadding ->
    when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
      is RegistrationScaffold.Params.OnePane -> OnePaneLayout(
        params = layoutParams,
        innerPadding = innerPadding,
        state = state,
        onEvent = onEvent
      )

      is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(
        params = layoutParams,
        innerPadding = innerPadding,
        state = state,
        onEvent = onEvent
      )
    }
  }
}

/**
 * Modal dialogs for failures that occur while requesting a verification code (resend SMS / call me). Unlike the
 * inline snackbars used for code submission, these block until acknowledged so the user can't miss them.
 */
@Composable
private fun RequestCodeErrorDialogs(dialogs: VerificationCodeState.Dialogs, onEvent: (VerificationCodeScreenEvents) -> Unit) {
  dialogs.providerRejectedTransport?.let { transport ->
    val message = when (transport) {
      VerificationCodeTransport.VOICE -> stringResource(R.string.VerificationCodeScreen__could_not_call_provider_rejected)
      VerificationCodeTransport.SMS -> stringResource(R.string.VerificationCodeScreen__could_not_sms_provider_rejected)
    }
    Dialogs.SimpleMessageDialog(
      message = message,
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(VerificationCodeScreenEvents.ProviderRejectedDialogDismissed) }
    )
    return
  }

  val simpleError: Pair<String, VerificationCodeScreenEvents>? = when {
    dialogs.networkError -> stringResource(R.string.VerificationCodeScreen__network_error) to VerificationCodeScreenEvents.NetworkErrorDialogDismissed
    dialogs.rateLimitedRetryAfter != null -> {
      val message = if (dialogs.rateLimitedRetryAfter.isPositive()) {
        stringResource(R.string.VerificationCodeScreen__too_many_attempts_try_again_in_s, dialogs.rateLimitedRetryAfter.toString())
      } else {
        stringResource(R.string.VerificationCodeScreen__too_many_attempts)
      }
      message to VerificationCodeScreenEvents.RateLimitedDialogDismissed
    }
    dialogs.couldNotRequestCodeWithSelectedTransport -> stringResource(R.string.VerificationCodeScreen__could_not_send_code_via_selected_method) to VerificationCodeScreenEvents.CouldNotRequestCodeWithSelectedTransportDialogDismissed
    dialogs.unableToSendSms -> stringResource(R.string.VerificationCodeScreen__unable_to_send_sms) to VerificationCodeScreenEvents.UnableToSendSmsDialogDismissed
    dialogs.unknownError -> stringResource(R.string.VerificationCodeScreen__an_unexpected_error_occurred) to VerificationCodeScreenEvents.UnknownErrorDialogDismissed
    else -> null
  }

  simpleError?.let { (message, dismissedEvent) ->
    Dialogs.SimpleMessageDialog(
      message = message,
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(dismissedEvent) }
    )
  }
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  innerPadding: PaddingValues,
  state: VerificationCodeState,
  onEvent: (VerificationCodeScreenEvents) -> Unit
) {
  val scrollState = rememberScrollState()

  OnePaneRegistrationScaffold(
    modifier = Modifier
      .fillMaxSize()
      .padding(innerPadding)
      .consumeWindowInsets(innerPadding),
    params = params,
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        Description(state, onEvent)

        Spacer(modifier = Modifier.height(32.dp))

        CodeField(state, onEvent)

        Spacer(modifier = Modifier.height(32.dp))

        if (state.shouldShowHavingTrouble()) {
          TroubleButton(onEvent)
        }
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
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          AlternateCodeOptions(state, onEvent)
        }
      }
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  innerPadding: PaddingValues,
  state: VerificationCodeState,
  onEvent: (VerificationCodeScreenEvents) -> Unit
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    modifier = Modifier
      .fillMaxSize()
      .padding(innerPadding)
      .consumeWindowInsets(innerPadding),
    params = params,
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        Description(state, onEvent, twoPane = true)
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      ) {
        CodeField(state, onEvent)

        Spacer(modifier = Modifier.height(32.dp))

        if (state.shouldShowHavingTrouble()) {
          TroubleButton(onEvent)
        }
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
          AlternateCodeOptions(state, onEvent)
        }
      }
    }
  )
}

@Composable
private fun TroubleButton(onEvent: (VerificationCodeScreenEvents) -> Unit) {
  TextButton(
    onClick = { onEvent(VerificationCodeScreenEvents.HavingTrouble) },
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentWidth(Alignment.CenterHorizontally)
      .testTag(TestTags.VERIFICATION_CODE_HAVING_TROUBLE_BUTTON)
  ) {
    Text(
      text = stringResource(R.string.VerificationCodeScreen__having_trouble),
      color = MaterialTheme.colorScheme.primary
    )
  }
}

@Composable
private fun CodeField(state: VerificationCodeState, onEvent: (VerificationCodeScreenEvents) -> Unit) {
  Column(modifier = Modifier.fillMaxWidth()) {
    CodeEntryField(
      state = state.codeEntry,
      onEvent = { onEvent(VerificationCodeScreenEvents.CodeEntryEvent(it)) },
      enabled = !state.isSubmittingCode,
      digitSpacing = 4.dp,
      separatorPadding = 8.dp,
      modifier = Modifier.contentType(ContentType.SmsOtpCode)
    )

    if (state.isSubmittingCode) {
      Spacer(modifier = Modifier.height(16.dp))
      CircularProgressIndicator(
        modifier = Modifier
          .size(48.dp)
          .align(Alignment.CenterHorizontally)
      )
    }
  }
}

@Composable
private fun AlternateCodeOptions(state: VerificationCodeState, onEvent: (VerificationCodeScreenEvents) -> Unit) {
  val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

  val canResendSms = state.canResendSms()
  val smsCountdown = state.smsResendCountdown()
  TextButton(
    onClick = { onEvent(VerificationCodeScreenEvents.ResendSms) },
    enabled = canResendSms,
    modifier = Modifier
      .testTag(TestTags.VERIFICATION_CODE_RESEND_SMS_BUTTON)
  ) {
    Text(
      text = if (smsCountdown != null) {
        val totalSeconds = smsCountdown.inWholeSeconds.toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        stringResource(R.string.VerificationCodeScreen__resend_code_available_in, minutes, seconds)
      } else {
        stringResource(R.string.VerificationCodeScreen__resend_code)
      },
      color = if (canResendSms) MaterialTheme.colorScheme.primary else disabledColor,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.labelLarge
    )
  }

  Spacer(modifier = Modifier.width(8.dp))

  val canRequestCall = state.canRequestCall()
  val callCountdown = state.callRequestCountdown()
  TextButton(
    onClick = { onEvent(VerificationCodeScreenEvents.CallMe) },
    enabled = canRequestCall,
    modifier = Modifier
      .testTag(TestTags.VERIFICATION_CODE_CALL_ME_BUTTON)
  ) {
    Text(
      text = if (callCountdown != null) {
        val totalSeconds = callCountdown.inWholeSeconds.toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        stringResource(R.string.VerificationCodeScreen__call_me_available_in, minutes, seconds)
      } else {
        stringResource(R.string.VerificationCodeScreen__call_me_instead)
      },
      color = if (canRequestCall) MaterialTheme.colorScheme.primary else disabledColor,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.labelLarge
    )
  }
}

@Composable
private fun Description(state: VerificationCodeState, onEvent: (VerificationCodeScreenEvents) -> Unit, twoPane: Boolean = false) {
  Text(
    text = stringResource(R.string.VerificationCodeScreen__verification_code),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.VerificationCodeScreen__enter_the_code_we_sent_to_s, state.e164),
    style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp)
  )

  Spacer(modifier = Modifier.height(8.dp))

  TextButton(
    onClick = { onEvent(VerificationCodeScreenEvents.WrongNumber) },
    contentPadding = PaddingValues(horizontal = 16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentWidth(Alignment.Start)
      .testTag(TestTags.VERIFICATION_CODE_WRONG_NUMBER_BUTTON)
  ) {
    Text(
      text = stringResource(R.string.VerificationCodeScreen__wrong_number),
      color = MaterialTheme.colorScheme.primary
    )
  }
}

@AllDevicePreviews
@Composable
private fun VerificationCodeScreenPreview() {
  Previews.Preview {
    VerificationCodeScreen(
      state = VerificationCodeState(
        e164 = "+1 555-123-4567"
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun VerificationCodeScreenWithCountdownPreview() {
  Previews.Preview {
    VerificationCodeScreen(
      state = VerificationCodeState(
        e164 = "+1 555-123-4567",
        rateLimits = SmsAndCallRateLimits(
          smsResendTimeRemaining = 45.seconds,
          callRequestTimeRemaining = 64.seconds
        )
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun VerificationCodeScreenSubmittingPreview() {
  Previews.Preview {
    VerificationCodeScreen(
      state = VerificationCodeState(
        e164 = "+1 555-123-4567",
        isSubmittingCode = true
      ),
      onEvent = {}
    )
  }
}
