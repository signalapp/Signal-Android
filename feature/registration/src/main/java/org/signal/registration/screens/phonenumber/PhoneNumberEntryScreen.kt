/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.IconButtons.IconButton
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScreen
import org.signal.registration.screens.phonenumber.PhoneNumberEntryState.OneTimeEvent
import org.signal.registration.test.TestTags

/**
 * Phone number entry screen
 */
@Composable
fun PhoneNumberScreen(
  state: PhoneNumberEntryState,
  onEvent: (PhoneNumberEntryScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val resources = LocalResources.current
  var simpleErrorMessage: String? by remember { mutableStateOf(null) }
  val windowBreakpoint = rememberWindowBreakpoint()

  if (state.showDialog) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.RegistrationActivity_is_the_phone_number),
      body = "+${state.countryCode} ${state.formattedNumber}\n\n${stringResource(R.string.RegistrationActivity_a_verification_code)}",
      confirm = stringResource(id = android.R.string.ok),
      dismiss = stringResource(R.string.RegistrationActivity_edit_number),
      onConfirm = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberSubmitted) },
      onDismiss = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberCancelled) }
    )
  }

  LaunchedEffect(state.oneTimeEvent) {
    onEvent(PhoneNumberEntryScreenEvents.ConsumeOneTimeEvent)
    when (state.oneTimeEvent) {
      OneTimeEvent.NetworkError -> simpleErrorMessage = resources.getString(R.string.VerificationCodeScreen__network_error)
      is OneTimeEvent.RateLimited -> simpleErrorMessage = resources.getString(R.string.VerificationCodeScreen__too_many_attempts_try_again_in_s, state.oneTimeEvent.retryAfter.toString())
      OneTimeEvent.UnknownError -> simpleErrorMessage = resources.getString(R.string.VerificationCodeScreen__an_unexpected_error_occurred)
      OneTimeEvent.CouldNotRequestCodeWithSelectedTransport -> simpleErrorMessage = resources.getString(R.string.VerificationCodeScreen__could_not_send_code_via_selected_method)
      OneTimeEvent.UnableToSendSms -> simpleErrorMessage = resources.getString(R.string.VerificationCodeScreen__unable_to_send_sms)
      null -> Unit
    }
  }

  simpleErrorMessage?.let { message ->
    Dialogs.SimpleMessageDialog(
      message = message,
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { simpleErrorMessage = null }
    )
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.PHONE_NUMBER_SCREEN)
  ) {
    when (windowBreakpoint) {
      WindowBreakpoint.SMALL -> {
        CompactLayout(state, onEvent)
      }

      WindowBreakpoint.MEDIUM -> {
        MediumLayout(state, onEvent)
      }

      WindowBreakpoint.LARGE -> {
        LargeLayout(state, onEvent)
      }
    }
  }
}

@Composable
fun TopbarMenu() {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.End
  ) {
    Box {
      val menuController = remember { DropdownMenus.MenuController() }
      IconButton(
        onClick = { menuController.show() }
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(org.signal.core.ui.R.drawable.symbol_more_vertical_24),
          contentDescription = stringResource(R.string.RegistrationActivity_open_menu)
        )
      }

      DropdownMenus.Menu(
        controller = menuController,
        offsetX = 24.dp,
        offsetY = 0.dp
      ) {
        DropdownMenus.Item(
          text = {
            Text(text = stringResource(R.string.RegistrationActivity_use_proxy))
          },
          onClick = {
            // TODO: Implement use proxy
            menuController.hide()
          }
        )
        DropdownMenus.Item(
          text = {
            Text(text = stringResource(R.string.RegistrationActivity_link_device))
          },
          onClick = {
            // TODO: Implement link device
            menuController.hide()
          }
        )
      }
    }
  }
}

@Composable
private fun CompactLayout(state: PhoneNumberEntryState, onEvent: (PhoneNumberEntryScreenEvents) -> Unit) {
  val selectedCountry = state.countryName
  val selectedCountryEmoji = state.countryEmoji

  val scrollState = rememberScrollState()

  RegistrationScreen(
    modifier = Modifier,
    header = {
      TopbarMenu()
    },
    content = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        Description()

        Spacer(modifier = Modifier.height(36.dp))

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
          hasValidCountry = state.countryName.isNotEmpty(),
          countryCode = state.countryCode,
          formattedNumber = state.formattedNumber,
          onCountryCodeChanged = { onEvent(PhoneNumberEntryScreenEvents.CountryCodeChanged(it)) },
          onPhoneNumberChanged = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberChanged(it)) },
          onPhoneNumberEntered = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberEntered) },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
        )
      }
    },
    footer = {
      NextButton(state, onEvent)
    }
  )
}

@Composable
private fun MediumLayout(state: PhoneNumberEntryState, onEvent: (PhoneNumberEntryScreenEvents) -> Unit) {
  val selectedCountry = state.countryName
  val selectedCountryEmoji = state.countryEmoji

  val scrollState = rememberScrollState()

  RegistrationScreen(
    modifier = Modifier,
    header = {
      TopbarMenu()
    },
    content = {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
          Spacer(modifier = Modifier.height(56.dp))

          Description()
        }

        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
          Spacer(modifier = Modifier.height(56.dp))

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
            hasValidCountry = state.countryName.isNotEmpty(),
            countryCode = state.countryCode,
            formattedNumber = state.formattedNumber,
            onCountryCodeChanged = { onEvent(PhoneNumberEntryScreenEvents.CountryCodeChanged(it)) },
            onPhoneNumberChanged = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberChanged(it)) },
            onPhoneNumberEntered = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberEntered) },
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp)
          )
        }
      }
    },
    footer = {
      NextButton(state, onEvent)
    }
  )
}

@Composable
private fun LargeLayout(state: PhoneNumberEntryState, onEvent: (PhoneNumberEntryScreenEvents) -> Unit) {
  val selectedCountry = state.countryName
  val selectedCountryEmoji = state.countryEmoji

  val scrollState = rememberScrollState()

  RegistrationScreen(
    modifier = Modifier,
    header = {
      TopbarMenu()
    },
    content = {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
          Spacer(modifier = Modifier.height(56.dp))

          Description()
        }

        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
          Spacer(modifier = Modifier.height(56.dp))

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
            hasValidCountry = state.countryName.isNotEmpty(),
            countryCode = state.countryCode,
            formattedNumber = state.formattedNumber,
            onCountryCodeChanged = { onEvent(PhoneNumberEntryScreenEvents.CountryCodeChanged(it)) },
            onPhoneNumberChanged = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberChanged(it)) },
            onPhoneNumberEntered = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberEntered) },
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp)
          )
        }
      }
    },
    footer = {
      NextButton(state, onEvent)
    }
  )
}

@Composable
private fun Description() {
  Text(
    text = stringResource(R.string.RegistrationActivity_phone_number),
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp)
  )

  Spacer(modifier = Modifier.height(16.dp))

  Text(
    text = stringResource(R.string.RegistrationActivity_you_will_receive_a_verification_code),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp)
  )
}

@Composable
private fun NextButton(
  state: PhoneNumberEntryState,
  onEvent: (PhoneNumberEntryScreenEvents) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 32.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Buttons.LargeTonal(
      onClick = { onEvent(PhoneNumberEntryScreenEvents.PhoneNumberEntered) },
      enabled = !state.showSpinner && state.isNumberPossible,
      modifier = Modifier.testTag(TestTags.PHONE_NUMBER_NEXT_BUTTON)
    ) {
      if (state.showSpinner) {
        CircularProgressIndicator(
          modifier = Modifier.size(24.dp),
          strokeWidth = 3.dp,
          color = MaterialTheme.colorScheme.primary
        )
      } else {
        Text(stringResource(R.string.RegistrationActivity_next))
      }
    }
  }
}

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
      if (emoji.isNotEmpty()) {
        Text(
          text = emoji,
          fontSize = 24.sp
        )

        Spacer(modifier = Modifier.width(16.dp))
      }

      Text(
        text = country.takeIf { country.isNotEmpty() } ?: stringResource(R.string.RegistrationActivity_select_a_country),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f)
      )

      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.symbol_drop_down_24),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

/**
 * Phone number input fields containing the country code and phone number text fields.
 */
@Composable
private fun PhoneNumberInputFields(
  hasValidCountry: Boolean,
  countryCode: String,
  formattedNumber: String,
  onCountryCodeChanged: (String) -> Unit,
  onPhoneNumberChanged: (String) -> Unit,
  onPhoneNumberEntered: () -> Unit,
  modifier: Modifier = Modifier
) {
  var phoneNumberTextFieldValue by remember { mutableStateOf(TextFieldValue(formattedNumber)) }
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(formattedNumber) {
    if (phoneNumberTextFieldValue.text != formattedNumber) {
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

  LaunchedEffect(hasValidCountry) {
    if (hasValidCountry) {
      focusRequester.requestFocus()
    }
  }

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.Bottom
  ) {
    TextField(
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
      ),
      colors = TextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
      )
    )

    Spacer(modifier = Modifier.width(20.dp))

    TextField(
      value = phoneNumberTextFieldValue,
      onValueChange = { newValue ->
        phoneNumberTextFieldValue = newValue
        onPhoneNumberChanged(newValue.text)
      },
      modifier = Modifier
        .weight(1f)
        .focusRequester(focusRequester)
        .testTag(TestTags.PHONE_NUMBER_PHONE_FIELD),
      label = {
        Text(stringResource(R.string.RegistrationActivity_phone_number_description))
      },
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Phone,
        imeAction = ImeAction.Done
      ),
      keyboardActions = KeyboardActions(
        onDone = { onPhoneNumberEntered() }
      ),
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface
      ),
      colors = TextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
      )
    )
  }
}

@AllDevicePreviews
@Composable
private fun PhoneNumberScreenPreview() {
  Previews.Preview {
    PhoneNumberScreen(
      state = PhoneNumberEntryState(),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PhoneNumberScreenSpinnerPreview() {
  Previews.Preview {
    PhoneNumberScreen(
      state = PhoneNumberEntryState(showSpinner = true),
      onEvent = {}
    )
  }
}
