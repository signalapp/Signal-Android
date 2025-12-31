/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.registration.test.TestTags

/**
 * Verification code entry screen for the registration flow.
 * Displays a 6-digit code input in XXX-XXX format.
 */
@Composable
fun VerificationCodeScreen(
  state: VerificationCodeState,
  onEvent: (VerificationCodeScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var digits by remember { mutableStateOf(List(6) { "" }) }
  val focusRequesters = remember { List(6) { FocusRequester() } }

  // Auto-submit when all digits are entered
  LaunchedEffect(digits) {
    if (digits.all { it.isNotEmpty() }) {
      val code = digits.joinToString("")
      onEvent(VerificationCodeScreenEvents.CodeEntered(code))
    }
  }

  LaunchedEffect(state.oneTimeEvent) {
    onEvent(VerificationCodeScreenEvents.ConsumeInnerOneTimeEvent)

    when (state.oneTimeEvent) {
      VerificationCodeState.OneTimeEvent.CouldNotRequestCodeWithSelectedTransport -> { }
      VerificationCodeState.OneTimeEvent.IncorrectVerificationCode -> { }
      VerificationCodeState.OneTimeEvent.NetworkError -> { }
      is VerificationCodeState.OneTimeEvent.RateLimited -> { }
      VerificationCodeState.OneTimeEvent.ThirdPartyError -> { }
      VerificationCodeState.OneTimeEvent.UnknownError -> { }
      VerificationCodeState.OneTimeEvent.RegistrationError -> { }
      null -> { }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Top
  ) {
    Spacer(modifier = Modifier.height(48.dp))

    Text(
      text = "Enter verification code",
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Enter the code we sent to ${state.e164}",
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(32.dp))

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
          onValueChange = { newValue ->
            if (newValue.length <= 1 && (newValue.isEmpty() || newValue.all { it.isDigit() })) {
              digits = digits.toMutableList().apply { this[i] = newValue }
              if (newValue.isNotEmpty() && i < 5) {
                focusRequesters[i + 1].requestFocus()
              }
            }
          },
          focusRequester = focusRequesters[i],
          testTag = when (i) {
            0 -> TestTags.VERIFICATION_CODE_DIGIT_0
            1 -> TestTags.VERIFICATION_CODE_DIGIT_1
            else -> TestTags.VERIFICATION_CODE_DIGIT_2
          }
        )
        if (i < 2) {
          Spacer(modifier = Modifier.width(4.dp))
        }
      }

      // Separator
      Text(
        text = "-",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(horizontal = 8.dp)
      )

      // Last three digits
      for (i in 3..5) {
        if (i > 3) {
          Spacer(modifier = Modifier.width(4.dp))
        }
        DigitField(
          value = digits[i],
          onValueChange = { newValue ->
            if (newValue.length <= 1 && (newValue.isEmpty() || newValue.all { it.isDigit() })) {
              digits = digits.toMutableList().apply { this[i] = newValue }
              if (newValue.isNotEmpty() && i < 5) {
                focusRequesters[i + 1].requestFocus()
              }
            }
          },
          focusRequester = focusRequesters[i],
          testTag = when (i) {
            3 -> TestTags.VERIFICATION_CODE_DIGIT_3
            4 -> TestTags.VERIFICATION_CODE_DIGIT_4
            else -> TestTags.VERIFICATION_CODE_DIGIT_5
          }
        )
      }
    }

    Spacer(modifier = Modifier.height(32.dp))

    TextButton(
      onClick = { onEvent(VerificationCodeScreenEvents.WrongNumber) },
      modifier = Modifier.testTag(TestTags.VERIFICATION_CODE_WRONG_NUMBER_BUTTON)
    ) {
      Text("Wrong number?")
    }

    Spacer(modifier = Modifier.weight(1f))

    TextButton(
      onClick = { onEvent(VerificationCodeScreenEvents.ResendSms) },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.VERIFICATION_CODE_RESEND_SMS_BUTTON)
    ) {
      Text("Resend SMS")
    }

    TextButton(
      onClick = { onEvent(VerificationCodeScreenEvents.CallMe) },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.VERIFICATION_CODE_CALL_ME_BUTTON)
    ) {
      Text("Call me instead")
    }
  }

  // Auto-focus first field on initial composition
  LaunchedEffect(Unit) {
    focusRequesters[0].requestFocus()
  }
}

/**
 * Individual digit input field
 */
@Composable
private fun DigitField(
  value: String,
  onValueChange: (String) -> Unit,
  focusRequester: FocusRequester,
  testTag: String,
  modifier: Modifier = Modifier
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier
      .width(44.dp)
      .focusRequester(focusRequester)
      .testTag(testTag),
    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
  )
}

@DayNightPreviews
@Composable
private fun VerificationCodeScreenPreview() {
  Previews.Preview {
    VerificationCodeScreen(
      state = VerificationCodeState(),
      onEvent = {}
    )
  }
}
