/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
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
  var digits by remember { mutableStateOf(List(6) { "" }) }
  val focusRequesters = remember { List(6) { FocusRequester() } }
  val scrollState = rememberScrollState()
  val snackbarHostState = remember { SnackbarHostState() }

  // Preload error strings for use in LaunchedEffect
  val incorrectCodeMsg = stringResource(R.string.VerificationCodeScreen__incorrect_code)
  val networkErrorMsg = stringResource(R.string.VerificationCodeScreen__network_error)
  val unknownErrorMsg = stringResource(R.string.VerificationCodeScreen__an_unexpected_error_occurred)
  val smsProviderErrorMsg = stringResource(R.string.VerificationCodeScreen__sms_provider_error)
  val transportErrorMsg = stringResource(R.string.VerificationCodeScreen__could_not_send_code_via_selected_method)
  val registrationErrorMsg = stringResource(R.string.VerificationCodeScreen__registration_error)
  // Preformat the rate-limited message template
  val rateLimitedEvent = state.oneTimeEvent as? VerificationCodeState.OneTimeEvent.RateLimited
  val rateLimitedMsg = if (rateLimitedEvent != null) {
    stringResource(R.string.VerificationCodeScreen__too_many_attempts_try_again_in_s, rateLimitedEvent.retryAfter.toString())
  } else {
    ""
  }

  // Countdown timer effect - emits CountdownTick every second while timers are active
  LaunchedEffect(state.rateLimits) {
    if (state.rateLimits.smsResendTimeRemaining > 0.seconds || state.rateLimits.callRequestTimeRemaining > 0.seconds) {
      while (true) {
        delay(1000)
        onEvent(VerificationCodeScreenEvents.CountdownTick)
      }
    }
  }

  // Auto-submit when all digits are entered
  LaunchedEffect(digits) {
    if (digits.all { it.isNotEmpty() } && !state.isSubmittingCode) {
      val code = digits.joinToString("")
      onEvent(VerificationCodeScreenEvents.CodeEntered(code))
    }
  }

  // Handle one-time events — handle first, then consume
  LaunchedEffect(state.oneTimeEvent) {
    val event = state.oneTimeEvent ?: return@LaunchedEffect

    when (event) {
      VerificationCodeState.OneTimeEvent.IncorrectVerificationCode -> {
        digits = List(6) { "" }
        focusRequesters[0].requestFocus()
        snackbarHostState.showSnackbar(incorrectCodeMsg)
      }
      VerificationCodeState.OneTimeEvent.NetworkError -> {
        snackbarHostState.showSnackbar(networkErrorMsg)
      }
      is VerificationCodeState.OneTimeEvent.RateLimited -> {
        snackbarHostState.showSnackbar(rateLimitedMsg)
      }
      VerificationCodeState.OneTimeEvent.ThirdPartyError -> {
        snackbarHostState.showSnackbar(smsProviderErrorMsg)
      }
      VerificationCodeState.OneTimeEvent.CouldNotRequestCodeWithSelectedTransport -> {
        snackbarHostState.showSnackbar(transportErrorMsg)
      }
      VerificationCodeState.OneTimeEvent.UnknownError -> {
        snackbarHostState.showSnackbar(unknownErrorMsg)
      }
      VerificationCodeState.OneTimeEvent.RegistrationError -> {
        snackbarHostState.showSnackbar(registrationErrorMsg)
      }
    }

    onEvent(VerificationCodeScreenEvents.ConsumeInnerOneTimeEvent)
  }

  // Auto-focus first field on initial composition
  LaunchedEffect(Unit) {
    focusRequesters[0].requestFocus()
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    modifier = modifier
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(scrollState)
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(40.dp))

      // Header
      Text(
        text = stringResource(R.string.VerificationCodeScreen__verification_code),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentWidth(Alignment.Start)
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Subheader with phone number
      Text(
        text = stringResource(R.string.VerificationCodeScreen__enter_the_code_we_sent_to_s, state.e164),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentWidth(Alignment.Start)
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Wrong number button - aligned to start like in XML
      TextButton(
        onClick = { onEvent(VerificationCodeScreenEvents.WrongNumber) },
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

      Spacer(modifier = Modifier.height(32.dp))

      // Code input with spinner overlay when submitting
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        // Code input fields - XXX-XXX format
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.VERIFICATION_CODE_INPUT),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // First three digits
          for (i in 0..2) {
            DigitField(
              value = digits[i],
              onValueChange = { newValue, isBackspace ->
                handleDigitChange(
                  index = i,
                  newValue = newValue,
                  isBackspace = isBackspace,
                  digits = digits,
                  focusRequesters = focusRequesters,
                  onDigitsChanged = { digits = it }
                )
              },
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

          // Separator
          Text(
            text = "-",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
            color = if (state.isSubmittingCode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
          )

          // Last three digits
          for (i in 3..5) {
            if (i > 3) {
              Spacer(modifier = Modifier.width(4.dp))
            }
            DigitField(
              value = digits[i],
              onValueChange = { newValue, isBackspace ->
                handleDigitChange(
                  index = i,
                  newValue = newValue,
                  isBackspace = isBackspace,
                  digits = digits,
                  focusRequesters = focusRequesters,
                  onDigitsChanged = { digits = it }
                )
              },
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

        // Loading spinner overlay
        if (state.isSubmittingCode) {
          CircularProgressIndicator(
            modifier = Modifier.size(48.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(32.dp))

      // Having trouble button - shown after 3 incorrect code attempts (matching old behavior)
      if (state.shouldShowHavingTrouble()) {
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

      Spacer(modifier = Modifier.weight(1f))

      // Bottom buttons - Resend SMS and Call Me side by side
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        // Resend SMS button with countdown — fits on one line if space allows, wraps if not
        val canResendSms = state.canResendSms()
        val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        TextButton(
          onClick = { onEvent(VerificationCodeScreenEvents.ResendSms) },
          enabled = canResendSms,
          modifier = Modifier
            .weight(1f)
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
            textAlign = TextAlign.Center
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Call Me button with inline countdown
        val canRequestCall = state.canRequestCall()
        TextButton(
          onClick = { onEvent(VerificationCodeScreenEvents.CallMe) },
          enabled = canRequestCall,
          modifier = Modifier
            .weight(1f)
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
            textAlign = TextAlign.Center
          )
        }
      }
    }
  }
}

/**
 * Handles digit input changes including navigation between fields and backspace handling.
 */
private fun handleDigitChange(
  index: Int,
  newValue: String,
  isBackspace: Boolean,
  digits: List<String>,
  focusRequesters: List<FocusRequester>,
  onDigitsChanged: (List<String>) -> Unit
) {
  when {
    // Handle backspace on empty field - move to previous field
    isBackspace && newValue.isEmpty() && index > 0 -> {
      val newDigits = digits.toMutableList().apply { this[index] = "" }
      onDigitsChanged(newDigits)
      focusRequesters[index - 1].requestFocus()
    }
    // Handle new digit input
    newValue.length <= 1 && (newValue.isEmpty() || newValue.all { it.isDigit() }) -> {
      val newDigits = digits.toMutableList().apply { this[index] = newValue }
      onDigitsChanged(newDigits)
      // Move to next field if digit entered and not last field
      if (newValue.isNotEmpty() && index < 5) {
        focusRequesters[index + 1].requestFocus()
      }
    }
  }
}

/**
 * Individual digit input field with backspace handling.
 */
@Composable
private fun DigitField(
  value: String,
  onValueChange: (String, Boolean) -> Unit,
  focusRequester: FocusRequester,
  testTag: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  OutlinedTextField(
    value = value,
    onValueChange = { newValue ->
      // Determine if this is a backspace (new value is empty and old value was not)
      val isBackspace = newValue.isEmpty() && value.isNotEmpty()
      onValueChange(newValue, isBackspace)
    },
    modifier = modifier
      .width(48.dp)
      .focusRequester(focusRequester)
      .testTag(testTag)
      .onKeyEvent { keyEvent ->
        // Handle hardware backspace key when field is empty
        if (keyEvent.type == KeyEventType.KeyUp &&
          (keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete) &&
          value.isEmpty()
        ) {
          onValueChange("", true)
          true
        } else {
          false
        }
      },
    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    enabled = enabled,
    colors = OutlinedTextFieldDefaults.colors(
      disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
      disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
  )
}

@DayNightPreviews
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

@DayNightPreviews
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

@DayNightPreviews
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
