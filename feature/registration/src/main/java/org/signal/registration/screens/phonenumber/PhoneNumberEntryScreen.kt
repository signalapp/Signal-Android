/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
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
  var simpleErrorMessage: String? by remember { mutableStateOf(null) }

  LaunchedEffect(state.oneTimeEvent) {
    onEvent(PhoneNumberEntryScreenEvents.ConsumeOneTimeEvent)
    when (state.oneTimeEvent) {
      OneTimeEvent.NetworkError -> simpleErrorMessage = "Network error"
      is OneTimeEvent.RateLimited -> simpleErrorMessage = "Rate limited"
      OneTimeEvent.UnknownError -> simpleErrorMessage = "Unknown error"
      OneTimeEvent.CouldNotRequestCodeWithSelectedTransport -> simpleErrorMessage = "Could not request code with selected transport"
      OneTimeEvent.ThirdPartyError -> simpleErrorMessage = "Third party error"
      null -> Unit
    }
  }

  simpleErrorMessage?.let { message ->
    Dialogs.SimpleMessageDialog(
      message = message,
      dismiss = "Ok",
      onDismiss = { simpleErrorMessage = null }
    )
  }

  Box(modifier = modifier.fillMaxSize().testTag(TestTags.PHONE_NUMBER_SCREEN)) {
    ScreenContent(state, onEvent)
  }
}

@Composable
private fun ScreenContent(state: PhoneNumberEntryState, onEvent: (PhoneNumberEntryScreenEvents) -> Unit) {
  val selectedCountry = state.countryName
  val selectedCountryEmoji = state.countryEmoji

  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
  ) {
    // Toolbar spacer (matching the Toolbar height in the XML)
    Spacer(modifier = Modifier.height(56.dp))

    // Title - "Phone number"
    Text(
      text = stringResource(R.string.RegistrationActivity_phone_number),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Subtitle - "You will receive a verification code..."
    Text(
      text = stringResource(R.string.RegistrationActivity_you_will_receive_a_verification_code),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
    )

    Spacer(modifier = Modifier.height(36.dp))

    // Country Picker - styled with surfaceVariant background and outline bottom border
    CountryPicker(
      emoji = selectedCountryEmoji,
      country = selectedCountry,
      onClick = { onEvent(PhoneNumberEntryScreenEvents.CountryPicker) },
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .testTag(TestTags.PHONE_NUMBER_COUNTRY_PICKER)
    )

    Spacer(modifier = Modifier.height(16.dp))

    PhoneNumberInputFields(
      countryCode = state.countryCode,
      formattedNumber = state.formattedNumber,
      onCountryCodeChanged = { onEvent(PhoneNumberEntryScreenEvents.CountryCodeChanged(it)) },
      onPhoneNumberChanged = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberChanged(it)) },
      onPhoneNumberSubmitted = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberSubmitted) },
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
    )

    Spacer(modifier = Modifier.weight(1f))

    // Bottom row with the next/spinner button aligned to end
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp, vertical = 16.dp),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically
    ) {
      CircularProgressWrapper(
        isLoading = state.showSpinner
      ) {
        Buttons.LargeTonal(
          onClick = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberSubmitted) },
          enabled = state.countryCode.isNotEmpty() && state.nationalNumber.isNotEmpty(),
          modifier = Modifier.testTag(TestTags.PHONE_NUMBER_NEXT_BUTTON)
        ) {
          Text(stringResource(R.string.RegistrationActivity_next))
        }
      }
    }
  }
}

/**
 * Country picker row styled to match the XML layout:
 * - surfaceVariant background with outline bottom border
 * - Rounded top corners (8dp outline, 4dp inner)
 * - Country emoji, country name, and dropdown triangle
 */
@Composable
private fun CountryPicker(
  emoji: String,
  country: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
      .background(MaterialTheme.colorScheme.outline)
      .padding(bottom = 1.dp)
      .background(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
      )
      .clickable(onClick = onClick)
      .height(56.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(start = 16.dp, end = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = emoji,
        fontSize = 24.sp
      )

      Spacer(modifier = Modifier.width(16.dp))

      Text(
        text = country,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f)
      )

      DropdownTriangle(
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

/**
 * Phone number input fields containing the country code and phone number text fields.
 * Handles cursor position preservation when the formatted number changes.
 */
@Composable
private fun PhoneNumberInputFields(
  countryCode: String,
  formattedNumber: String,
  onCountryCodeChanged: (String) -> Unit,
  onPhoneNumberChanged: (String) -> Unit,
  onPhoneNumberSubmitted: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Track the phone number text field value with cursor position
  var phoneNumberTextFieldValue by remember { mutableStateOf(TextFieldValue(formattedNumber)) }

  // Update the text field value when formattedNumber changes, preserving cursor position
  LaunchedEffect(formattedNumber) {
    if (phoneNumberTextFieldValue.text != formattedNumber) {
      // Calculate cursor position: count digits before cursor in old text,
      // then find position with same digit count in new text
      val oldText = phoneNumberTextFieldValue.text
      val oldCursorPos = phoneNumberTextFieldValue.selection.end
      val digitsBeforeCursor = oldText.take(oldCursorPos).count { it.isDigit() }

      var digitCount = 0
      var newCursorPos = formattedNumber.length
      for (i in formattedNumber.indices) {
        if (formattedNumber[i].isDigit()) {
          digitCount++
        }
        if (digitCount >= digitsBeforeCursor) {
          newCursorPos = i + 1
          break
        }
      }

      phoneNumberTextFieldValue = TextFieldValue(
        text = formattedNumber,
        selection = TextRange(newCursorPos)
      )
    }
  }

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.Bottom
  ) {
    // Country code field
    OutlinedTextField(
      value = countryCode,
      onValueChange = onCountryCodeChanged,
      modifier = Modifier
        .width(76.dp)
        .testTag(TestTags.PHONE_NUMBER_COUNTRY_CODE_FIELD),
      prefix = {
        Text(
          text = "+",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
      },
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done
      ),
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    )

    Spacer(modifier = Modifier.width(20.dp))

    // Phone number field
    OutlinedTextField(
      value = phoneNumberTextFieldValue,
      onValueChange = { newValue ->
        phoneNumberTextFieldValue = newValue
        onPhoneNumberChanged(newValue.text)
      },
      modifier = Modifier
        .weight(1f)
        .testTag(TestTags.PHONE_NUMBER_PHONE_FIELD),
      label = {
        Text(stringResource(R.string.RegistrationActivity_phone_number_description))
      },
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Phone,
        imeAction = ImeAction.Done
      ),
      keyboardActions = KeyboardActions(
        onDone = { onPhoneNumberSubmitted() }
      ),
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface
      )
    )
  }
}

/**
 * Simple dropdown triangle icon matching the symbol_dropdown_triangle_24 vector drawable.
 */
@Composable
private fun DropdownTriangle(
  tint: Color,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
      // Triangle pointing down, centered in the 18x24 viewport
      val scaleX = w / 18f
      val scaleY = h / 24f
      moveTo(5.2f * scaleX, 9.5f * scaleY)
      lineTo(12.8f * scaleX, 9.5f * scaleY)
      lineTo(9f * scaleX, 14.95f * scaleY)
      close()
    }
    drawPath(path, tint)
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
      state = PhoneNumberEntryState(showSpinner = true),
      onEvent = {}
    )
  }
}
