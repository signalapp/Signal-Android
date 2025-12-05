/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.registration.screens.phonenumber.PhoneNumberEntryState.OneTimeEvent
import org.signal.registration.test.TestTags

/**
 * Phone number entry screen for the registration flow.
 * Allows users to select their country and enter their phone number.
 */
@Composable
fun PhoneNumberScreen(
  state: PhoneNumberEntryState,
  onEvent: (PhoneNumberEntryScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  LaunchedEffect(state.oneTimeEvent) {
    onEvent(PhoneNumberEntryScreenEvents.ConsumeOneTimeEvent)
    when (state.oneTimeEvent) {
      OneTimeEvent.NetworkError -> TODO()
      is OneTimeEvent.RateLimited -> TODO()
      OneTimeEvent.UnknownError -> TODO()
      OneTimeEvent.CouldNotRequestCodeWithSelectedTransport -> TODO()
      OneTimeEvent.ThirdPartyError -> TODO()
      null -> Unit
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    ScreenContent(state, onEvent)

    if (state.showFullScreenSpinner) {
      Dialogs.IndeterminateProgressDialog()
    }
  }
}

@Composable
private fun ScreenContent(state: PhoneNumberEntryState, onEvent: (PhoneNumberEntryScreenEvents) -> Unit) {
  // TODO: These should come from state once country picker is implemented
  var selectedCountry by remember { mutableStateOf("United States") }
  var selectedCountryEmoji by remember { mutableStateOf("🇺🇸") }

  // Track the phone number text field value with cursor position
  var phoneNumberTextFieldValue by remember { mutableStateOf(TextFieldValue(state.formattedNumber)) }

  // Update the text field value when state.formattedNumber changes, preserving cursor position
  LaunchedEffect(state.formattedNumber) {
    if (phoneNumberTextFieldValue.text != state.formattedNumber) {
      // Calculate cursor position: count digits before cursor in old text,
      // then find position with same digit count in new text
      val oldText = phoneNumberTextFieldValue.text
      val oldCursorPos = phoneNumberTextFieldValue.selection.end
      val digitsBeforeCursor = oldText.take(oldCursorPos).count { it.isDigit() }

      val newText = state.formattedNumber
      var digitCount = 0
      var newCursorPos = newText.length
      for (i in newText.indices) {
        if (newText[i].isDigit()) {
          digitCount++
        }
        if (digitCount >= digitsBeforeCursor) {
          newCursorPos = i + 1
          break
        }
      }

      phoneNumberTextFieldValue = TextFieldValue(
        text = newText,
        selection = TextRange(newCursorPos)
      )
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.Start
  ) {
    // Title
    Text(
      text = "Phone number",
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Subtitle
    Text(
      text = "You will receive a verification code",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(36.dp))

    // Country Picker Button
    OutlinedButton(
      onClick = {
        onEvent(PhoneNumberEntryScreenEvents.CountryPicker)
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .testTag(TestTags.PHONE_NUMBER_COUNTRY_PICKER)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = selectedCountryEmoji,
          style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
          text = selectedCountry,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f)
        )
        Icon(
          painter = painterResource(android.R.drawable.arrow_down_float),
          contentDescription = "Select country",
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Phone number input fields
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // Country code field
      OutlinedTextField(
        value = state.countryCode,
        onValueChange = { onEvent(PhoneNumberEntryScreenEvents.CountryCodeChanged(it)) },
        modifier = Modifier
          .width(76.dp)
          .testTag(TestTags.PHONE_NUMBER_COUNTRY_CODE_FIELD),
        leadingIcon = {
          Text(
            text = "+",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        },
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Number,
          imeAction = ImeAction.Next
        ),
        singleLine = true
      )

      // Phone number field
      OutlinedTextField(
        value = phoneNumberTextFieldValue,
        onValueChange = { newValue ->
          phoneNumberTextFieldValue = newValue
          onEvent(PhoneNumberEntryScreenEvents.PhoneNumberChanged(newValue.text))
        },
        modifier = Modifier
          .weight(1f)
          .testTag(TestTags.PHONE_NUMBER_PHONE_FIELD),
        placeholder = {
          Text("Phone number")
        },
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Phone,
          imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
          onDone = {
            onEvent(PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)
          }
        ),
        singleLine = true
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    // Next button
    Button(
      onClick = {
        onEvent(PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .testTag(TestTags.PHONE_NUMBER_NEXT_BUTTON),
      enabled = state.countryCode.isNotEmpty() && state.nationalNumber.isNotEmpty()
    ) {
      Text("Next")
    }
  }
}

@DayNightPreviews
@Composable
private fun PhoneNumberScreenPreview() {
  Previews.Preview {
    PhoneNumberScreen(
      state = PhoneNumberEntryState(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PhoneNumberScreenSpinnerPreview() {
  Previews.Preview {
    PhoneNumberScreen(
      state = PhoneNumberEntryState(showFullScreenSpinner = true),
      onEvent = {}
    )
  }
}
