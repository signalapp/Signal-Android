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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScreen
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
  val snackbarHostState = remember { SnackbarHostState() }
  val windowBreakpoint = rememberWindowBreakpoint()
  val resources = LocalResources.current

  LaunchedEffect(state.rateLimits) {
    if (state.rateLimits.smsResendTimeRemaining > 0.seconds || state.rateLimits.callRequestTimeRemaining > 0.seconds) {
      while (true) {
        delay(1000)
        onEvent(VerificationCodeScreenEvents.CountdownTick)
      }
    }
  }

  LaunchedEffect(digits) {
    if (digits.all { it.isNotEmpty() } && !state.isSubmittingCode) {
      val code = digits.joinToString("")
      onEvent(VerificationCodeScreenEvents.CodeEntered(code))
    }
  }

  LaunchedEffect(state.oneTimeEvent) {
    val event = state.oneTimeEvent ?: return@LaunchedEffect

    when (event) {
      VerificationCodeState.OneTimeEvent.IncorrectVerificationCode -> {
        digits = List(6) { "" }
        focusRequesters[0].requestFocus()
        snackbarHostState.showSnackbar(resources.getString(R.string.VerificationCodeScreen__incorrect_code))
      }
      VerificationCodeState.OneTimeEvent.NetworkError -> {
        snackbarHostState.showSnackbar(resources.getString(R.string.VerificationCodeScreen__network_error))
      }
      is VerificationCodeState.OneTimeEvent.RateLimited -> {
        snackbarHostState.showSnackbar(resources.getString(R.string.VerificationCodeScreen__too_many_attempts_try_again_in_s, event.retryAfter.toString()))
      }
      VerificationCodeState.OneTimeEvent.UnableToSendSms -> {
        snackbarHostState.showSnackbar(resources.getString(R.string.VerificationCodeScreen__unable_to_send_sms))
      }
      VerificationCodeState.OneTimeEvent.CouldNotRequestCodeWithSelectedTransport -> {
        snackbarHostState.showSnackbar(resources.getString(R.string.VerificationCodeScreen__could_not_send_code_via_selected_method))
      }
      VerificationCodeState.OneTimeEvent.UnknownError -> {
        snackbarHostState.showSnackbar(resources.getString(R.string.VerificationCodeScreen__an_unexpected_error_occurred))
      }
      VerificationCodeState.OneTimeEvent.RegistrationError -> {
        snackbarHostState.showSnackbar(resources.getString(R.string.VerificationCodeScreen__registration_error))
      }
    }
    onEvent(VerificationCodeScreenEvents.ConsumeInnerOneTimeEvent)
  }

  LaunchedEffect(Unit) {
    focusRequesters[0].requestFocus()
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    modifier = modifier
  ) { innerPadding ->
    when (windowBreakpoint) {
      WindowBreakpoint.SMALL -> {
        CompactLayout(
          innerPadding = innerPadding,
          digits = digits,
          focusRequesters = focusRequesters,
          state = state,
          onEvent = onEvent,
          onDigitsChanged = { digits = it }
        )
      }

      WindowBreakpoint.MEDIUM -> {
        MediumLayout(
          digits = digits,
          focusRequesters = focusRequesters,
          state = state,
          onEvent = onEvent,
          onDigitsChanged = { digits = it }
        )
      }

      WindowBreakpoint.LARGE -> {
        LargeLayout(
          digits = digits,
          focusRequesters = focusRequesters,
          state = state,
          onEvent = onEvent,
          onDigitsChanged = { digits = it }
        )
      }
    }
  }
}

@Composable
private fun CompactLayout(
  innerPadding: PaddingValues,
  digits: List<String>,
  focusRequesters: List<FocusRequester>,
  state: VerificationCodeState,
  onEvent: (VerificationCodeScreenEvents) -> Unit,
  onDigitsChanged: (List<String>) -> Unit
) {
  val scrollState = rememberScrollState()

  RegistrationScreen(
    modifier = Modifier.fillMaxSize(),
    content = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(scrollState)
          .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(40.dp))

        Description(state, onEvent)

        Spacer(modifier = Modifier.height(32.dp))

        CodeField(
          digits = digits,
          focusRequesters = focusRequesters,
          state = state,
          onDigitsChanged = onDigitsChanged
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (state.shouldShowHavingTrouble()) {
          TroubleButton(onEvent)
        }
      }
    },
    footer = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom
      ) {
        AlternateCodeOptions(state, onEvent)
      }
    }
  )
}

@Composable
private fun MediumLayout(
  digits: List<String>,
  focusRequesters: List<FocusRequester>,
  state: VerificationCodeState,
  onEvent: (VerificationCodeScreenEvents) -> Unit,
  onDigitsChanged: (List<String>) -> Unit
) {
  val scrollState = rememberScrollState()
  RegistrationScreen(
    modifier = Modifier.fillMaxSize(),
    content = {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
          Spacer(modifier = Modifier.height(40.dp))

          Description(state, onEvent)
        }
        Column(
          modifier = Modifier.weight(1f)
        ) {
          Spacer(modifier = Modifier.height(40.dp))

          CodeField(
            digits = digits,
            focusRequesters = focusRequesters,
            state = state,
            onDigitsChanged = onDigitsChanged
          )

          Spacer(modifier = Modifier.height(32.dp))

          if (state.shouldShowHavingTrouble()) {
            TroubleButton(onEvent)
          }
        }
      }
    },
    footer = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp, start = 24.dp, end = 24.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
      ) {
        AlternateCodeOptions(state, onEvent)
      }
    }
  )
}

@Composable
private fun LargeLayout(
  digits: List<String>,
  focusRequesters: List<FocusRequester>,
  state: VerificationCodeState,
  onEvent: (VerificationCodeScreenEvents) -> Unit,
  onDigitsChanged: (List<String>) -> Unit
) {
  val scrollState = rememberScrollState()

  RegistrationScreen(
    modifier = Modifier.fillMaxSize(),
    content = {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
          Spacer(modifier = Modifier.height(40.dp))

          Description(state, onEvent)
        }
        Column(
          modifier = Modifier.weight(1f)
        ) {
          Spacer(modifier = Modifier.height(40.dp))

          CodeField(
            digits = digits,
            focusRequesters = focusRequesters,
            state = state,
            onDigitsChanged = onDigitsChanged
          )

          Spacer(modifier = Modifier.height(32.dp))

          if (state.shouldShowHavingTrouble()) {
            TroubleButton(onEvent)
          }
        }
      }
    },
    footer = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp, start = 24.dp, end = 24.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
      ) {
        AlternateCodeOptions(state, onEvent)
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
  digits: List<String>,
  focusRequesters: List<FocusRequester>,
  state: VerificationCodeState,
  onDigitsChanged: (List<String>) -> Unit
) {
  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center
  ) {
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
          onValueChange = { newValue, isBackspace ->
            handleDigitChange(
              index = i,
              newValue = newValue,
              isBackspace = isBackspace,
              digits = digits,
              focusRequesters = focusRequesters,
              onDigitsChanged = onDigitsChanged
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
          onValueChange = { newValue, isBackspace ->
            handleDigitChange(
              index = i,
              newValue = newValue,
              isBackspace = isBackspace,
              digits = digits,
              focusRequesters = focusRequesters,
              onDigitsChanged = onDigitsChanged
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

    if (state.isSubmittingCode) {
      CircularProgressIndicator(
        modifier = Modifier.size(48.dp)
      )
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
    modifier = Modifier.fillMaxWidth()
  )

  Spacer(modifier = Modifier.height(16.dp))

  Text(
    text = stringResource(R.string.VerificationCodeScreen__enter_the_code_we_sent_to_s, state.e164),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth()
  )

  Spacer(modifier = Modifier.height(8.dp))

  TextButton(
    onClick = { onEvent(VerificationCodeScreenEvents.WrongNumber) },
    contentPadding = PaddingValues(),
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

private fun handleDigitChange(
  index: Int,
  newValue: String,
  isBackspace: Boolean,
  digits: List<String>,
  focusRequesters: List<FocusRequester>,
  onDigitsChanged: (List<String>) -> Unit
) {
  if (isBackspace) {
    val deleteAt = if (digits[index].isNotEmpty()) index else index - 1
    if (deleteAt >= 0) {
      onDigitsChanged(
        digits.toMutableList().apply {
          for (j in deleteAt until 5) {
            this[j] = this[j + 1]
          }
          this[5] = ""
        }
      )
      focusRequesters[(index - 1).coerceAtLeast(0)].requestFocus()
    }
  } else if (newValue.isNotEmpty() && newValue[0].isDigit()) {
    onDigitsChanged(digits.toMutableList().apply { this[index] = newValue })
    focusRequesters[(index + 1).coerceAtMost(5)].requestFocus()
  }
}

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
      val capped = if (newValue.length > 1) {
        if (newValue.first().toString() == value) {
          newValue.last().toString()
        } else {
          newValue.first().toString()
        }
      } else newValue
      val isBackspace = capped.isEmpty() && value.isNotEmpty()
      onValueChange(capped, isBackspace)
    },
    modifier = modifier
      .width(48.dp)
      .focusRequester(focusRequester)
      .testTag(testTag)
      .onKeyEvent { keyEvent ->
        if ((keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete) && value.isEmpty()) {
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
