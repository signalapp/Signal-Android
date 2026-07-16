/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.test.TestTags
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
  val focusRequesters = remember { List(VerificationCodeState.CODE_LENGTH) { FocusRequester() } }
  val snackbarHostState = remember { SnackbarHostState() }
  val resources = LocalResources.current

  LaunchedEffect(state.rateLimits) {
    if (state.rateLimits.smsResendTimeRemaining > 0.seconds || state.rateLimits.callRequestTimeRemaining > 0.seconds) {
      while (true) {
        delay(1000)
        onEvent(VerificationCodeScreenEvents.CountdownTick)
      }
    }
  }

  LaunchedEffect(state.autoFillCode) {
    val code = state.autoFillCode ?: return@LaunchedEffect

    if (code.length == VerificationCodeState.CODE_LENGTH && code.all { it.isDigit() } && !state.isSubmittingCode) {
      onEvent(VerificationCodeScreenEvents.DigitChanged(0, code))
    }
    onEvent(VerificationCodeScreenEvents.ConsumeAutoFillCode)
  }

  LaunchedEffect(state.snackbars) {
    val (message, dismissedEvent) = when {
      state.snackbars.incorrectVerificationCode -> resources.getString(R.string.VerificationCodeScreen__incorrect_code) to VerificationCodeScreenEvents.IncorrectVerificationCodeSnackbarDismissed
      state.snackbars.networkError -> resources.getString(R.string.VerificationCodeScreen__network_error) to VerificationCodeScreenEvents.NetworkErrorSnackbarDismissed
      state.snackbars.rateLimitedRetryAfter != null -> resources.getString(R.string.VerificationCodeScreen__too_many_attempts_try_again_in_s, state.snackbars.rateLimitedRetryAfter.toString()) to VerificationCodeScreenEvents.RateLimitedSnackbarDismissed
      state.snackbars.unableToSendSms -> resources.getString(R.string.VerificationCodeScreen__unable_to_send_sms) to VerificationCodeScreenEvents.UnableToSendSmsSnackbarDismissed
      state.snackbars.couldNotRequestCodeWithSelectedTransport -> resources.getString(R.string.VerificationCodeScreen__could_not_send_code_via_selected_method) to VerificationCodeScreenEvents.CouldNotRequestCodeWithSelectedTransportSnackbarDismissed
      state.snackbars.unknownError -> resources.getString(R.string.VerificationCodeScreen__an_unexpected_error_occurred) to VerificationCodeScreenEvents.UnknownErrorSnackbarDismissed
      state.snackbars.registrationError -> resources.getString(R.string.VerificationCodeScreen__registration_error) to VerificationCodeScreenEvents.RegistrationErrorSnackbarDismissed
      else -> return@LaunchedEffect
    }

    snackbarHostState.showSnackbar(message)
    onEvent(dismissedEvent)
  }

  LaunchedEffect(state.focusedDigitIndex) {
    focusRequesters[state.focusedDigitIndex].requestFocus()
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    onEvent(VerificationCodeScreenEvents.Foregrounded)
  }

  if (state.showContactSupportSheet) {
    ContactSupportBottomSheet(
      onDismiss = { onEvent(VerificationCodeScreenEvents.DismissContactSupport) }
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
        focusRequesters = focusRequesters,
        state = state,
        onEvent = onEvent
      )

      is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(
        params = layoutParams,
        innerPadding = innerPadding,
        focusRequesters = focusRequesters,
        state = state,
        onEvent = onEvent
      )
    }
  }
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  innerPadding: PaddingValues,
  focusRequesters: List<FocusRequester>,
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

        CodeField(
          focusRequesters = focusRequesters,
          state = state,
          emitter = onEvent
        )

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
  focusRequesters: List<FocusRequester>,
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
        Description(state, onEvent)
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      ) {
        CodeField(
          focusRequesters = focusRequesters,
          state = state,
          emitter = onEvent
        )

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
private fun CodeField(
  focusRequesters: List<FocusRequester>,
  state: VerificationCodeState,
  emitter: (VerificationCodeScreenEvents) -> Unit
) {
  val digits = state.digits

  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center
  ) {
    Column(modifier = Modifier.align(Alignment.Center)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .testTag(TestTags.VERIFICATION_CODE_INPUT),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        for (i in 0..2) {
          DigitField(
            value = digits[i],
            onValueChange = { newValue -> emitter(VerificationCodeScreenEvents.DigitChanged(i, newValue)) },
            focusRequester = focusRequesters[i],
            testTag = when (i) {
              0 -> TestTags.VERIFICATION_CODE_DIGIT_0
              1 -> TestTags.VERIFICATION_CODE_DIGIT_1
              else -> TestTags.VERIFICATION_CODE_DIGIT_2
            },
            enabled = !state.isSubmittingCode
          )
          if (i < 2) {
            Spacer(modifier = Modifier.width(4.dp))
          }
        }

        Text(
          text = "-",
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.padding(horizontal = 8.dp),
          color = if (state.isSubmittingCode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
        )

        for (i in 3..5) {
          if (i > 3) {
            Spacer(modifier = Modifier.width(4.dp))
          }
          DigitField(
            value = digits[i],
            onValueChange = { newValue -> emitter(VerificationCodeScreenEvents.DigitChanged(i, newValue)) },
            focusRequester = focusRequesters[i],
            testTag = when (i) {
              3 -> TestTags.VERIFICATION_CODE_DIGIT_3
              4 -> TestTags.VERIFICATION_CODE_DIGIT_4
              else -> TestTags.VERIFICATION_CODE_DIGIT_5
            },
            enabled = !state.isSubmittingCode
          )
        }
      }

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
}

@Composable
private fun AlternateCodeOptions(state: VerificationCodeState, onEvent: (VerificationCodeScreenEvents) -> Unit) {
  val canResendSms = state.canResendSms()
  val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
  TextButton(
    onClick = { onEvent(VerificationCodeScreenEvents.ResendSms) },
    enabled = canResendSms,
    modifier = Modifier
      .testTag(TestTags.VERIFICATION_CODE_RESEND_SMS_BUTTON)
  ) {
    Text(
      text = if (canResendSms) {
        stringResource(R.string.VerificationCodeScreen__resend_code)
      } else {
        val totalSeconds = state.rateLimits.smsResendTimeRemaining.inWholeSeconds.toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        stringResource(R.string.VerificationCodeScreen__resend_code) + " " +
          stringResource(R.string.VerificationCodeScreen__countdown_format, minutes, seconds)
      },
      color = if (canResendSms) MaterialTheme.colorScheme.primary else disabledColor,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.labelLarge
    )
  }

  Spacer(modifier = Modifier.width(8.dp))

  val canRequestCall = state.canRequestCall()
  TextButton(
    onClick = { onEvent(VerificationCodeScreenEvents.CallMe) },
    enabled = canRequestCall,
    modifier = Modifier
      .testTag(TestTags.VERIFICATION_CODE_CALL_ME_BUTTON)
  ) {
    Text(
      text = if (canRequestCall) {
        stringResource(R.string.VerificationCodeScreen__call_me_instead)
      } else {
        val totalSeconds = state.rateLimits.callRequestTimeRemaining.inWholeSeconds.toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        stringResource(R.string.VerificationCodeScreen__call_me_available_in, minutes, seconds)
      },
      color = if (canRequestCall) MaterialTheme.colorScheme.primary else disabledColor,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.labelLarge
    )
  }
}

@Composable
private fun Description(state: VerificationCodeState, onEvent: (VerificationCodeScreenEvents) -> Unit) {
  Text(
    text = stringResource(R.string.VerificationCodeScreen__verification_code),
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.VerificationCodeScreen__enter_the_code_we_sent_to_s, state.e164),
    style = MaterialTheme.typography.bodyLarge,
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

@Composable
private fun DigitField(
  value: String,
  onValueChange: (String) -> Unit,
  focusRequester: FocusRequester,
  testTag: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  TextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier
      .width(48.dp)
      .focusRequester(focusRequester)
      .testTag(testTag)
      .onKeyEvent { keyEvent ->
        if ((keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete) && value.isEmpty()) {
          onValueChange("")
          true
        } else {
          false
        }
      },
    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
    singleLine = true,
    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    enabled = enabled,
    colors = TextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      focusedIndicatorColor = MaterialTheme.colorScheme.primary,
      unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
      disabledIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
      disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    )
  )
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
