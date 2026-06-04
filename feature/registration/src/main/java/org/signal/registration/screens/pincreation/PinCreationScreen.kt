/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper

/**
 * PIN creation screen for the registration flow.
 * Allows users to create a new PIN for their account.
 */
@Suppress("AssignedValueIsNeverRead")
@Composable
fun PinCreationScreen(
  state: PinCreationState,
  onEvent: (PinCreationScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var pin by rememberSaveable { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }
  val canSubmitPin = pin.length >= 4

  when (val params = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> OnePaneLayout(
      params = params,
      state = state,
      pin = pin,
      canSubmitPin = canSubmitPin,
      focusRequester = focusRequester,
      onPinChanged = { pin = it },
      onEvent = onEvent,
      modifier = modifier
    )

    is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(
      params = params,
      state = state,
      pin = pin,
      canSubmitPin = canSubmitPin,
      focusRequester = focusRequester,
      onPinChanged = { pin = it },
      onEvent = onEvent,
      modifier = modifier
    )
  }

  // autofocus PIN field on initial composition
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: PinCreationState,
  pin: String,
  canSubmitPin: Boolean,
  focusRequester: FocusRequester,
  onPinChanged: (String) -> Unit,
  onEvent: (PinCreationScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  RegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    content = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(params.panePadding(hasHeader = false))
      ) {
        PinDescription(
          isConfirmEnabled = state.isConfirmEnabled,
          onLearnMore = { onEvent(PinCreationScreenEvents.LearnMore) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        PinInputField(
          state = state,
          pin = pin,
          canSubmitPin = canSubmitPin,
          focusRequester = focusRequester,
          onPinChanged = onPinChanged,
          onSubmit = { onEvent(PinCreationScreenEvents.PinSubmitted(pin)) },
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        PinInputLabel(state)
        Spacer(modifier = Modifier.height(16.dp))

        KeyboardToggleButton(
          state = state,
          onToggleKeyboard = { onEvent(PinCreationScreenEvents.ToggleKeyboard) }
        )
      }
    },
    footer = {
      NextButton(
        params = params,
        canSubmitPin = canSubmitPin,
        showElevation = scrollState.canScrollForward,
        onNext = { onEvent(PinCreationScreenEvents.PinSubmitted(pin)) }
      )
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: PinCreationState,
  pin: String,
  canSubmitPin: Boolean,
  focusRequester: FocusRequester,
  onPinChanged: (String) -> Unit,
  onEvent: (PinCreationScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    params = params,
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .padding(paddingValues)
      ) {
        PinDescription(
          isConfirmEnabled = state.isConfirmEnabled,
          onLearnMore = { onEvent(PinCreationScreenEvents.LearnMore) }
        )
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        PinInputField(
          state = state,
          pin = pin,
          canSubmitPin = canSubmitPin,
          focusRequester = focusRequester,
          onPinChanged = onPinChanged,
          onSubmit = { onEvent(PinCreationScreenEvents.PinSubmitted(pin)) },
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        PinInputLabel(state)
        Spacer(modifier = Modifier.height(16.dp))
        KeyboardToggleButton(
          state = state,
          onToggleKeyboard = { onEvent(PinCreationScreenEvents.ToggleKeyboard) }
        )
      }
    },
    footer = {
      NextButton(
        params = params,
        canSubmitPin = canSubmitPin,
        showElevation = scrollState.canScrollForward,
        onNext = { onEvent(PinCreationScreenEvents.PinSubmitted(pin)) }
      )
    }
  )
}

@Composable
private fun PinDescription(
  isConfirmEnabled: Boolean,
  onLearnMore: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Text(
      text = when {
        isConfirmEnabled -> stringResource(R.string.PinCreationScreen__confirm_your_pin)
        else -> stringResource(R.string.PinCreationScreen__create_your_pin)
      },
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Start,
      modifier = Modifier
        .fillMaxWidth()
        .attachDebugLogHelper()
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (isConfirmEnabled) {
      Text(
        text = stringResource(R.string.PinCreationScreen__reenter_pin_description),
        style = MaterialTheme.typography.bodyLarge.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Start
        ),
        modifier = Modifier.fillMaxWidth()
      )
    } else {
      val descriptionText = buildAnnotatedString {
        append(stringResource(R.string.PinCreationScreen__pins_can_help))
        append(" ")
        pushStringAnnotation(tag = "LEARN_MORE", annotation = "learn_more")
        withStyle(
          style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
          )
        ) {
          append(stringResource(R.string.PinCreationScreen__learn_more))
        }
        pop()
      }

      ClickableText(
        text = descriptionText,
        style = MaterialTheme.typography.bodyLarge.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Start
        ),
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
          descriptionText.getStringAnnotations(tag = "LEARN_MORE", start = offset, end = offset)
            .firstOrNull()
            ?.let { onLearnMore() }
        }
      )
    }
  }
}

@Composable
private fun PinInputField(
  state: PinCreationState,
  pin: String,
  canSubmitPin: Boolean,
  focusRequester: FocusRequester,
  onPinChanged: (String) -> Unit,
  onSubmit: () -> Unit,
  modifier: Modifier = Modifier
) {
  TextField(
    value = pin,
    onValueChange = onPinChanged,
    modifier = modifier.focusRequester(focusRequester),
    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
    singleLine = true,
    keyboardOptions = KeyboardOptions(
      keyboardType = if (state.isAlphanumericKeyboard) KeyboardType.Password else KeyboardType.NumberPassword,
      imeAction = ImeAction.Done
    ),
    keyboardActions = KeyboardActions(onDone = { if (canSubmitPin) onSubmit() })
  )
}

@Composable
private fun PinInputLabel(
  state: PinCreationState,
  modifier: Modifier = Modifier
) {
  Text(
    text = when {
      state.isConfirmEnabled -> stringResource(R.string.PinCreationScreen__reenter_pin)
      state.isAlphanumericKeyboard -> stringResource(R.string.PinCreationScreen__pin_at_least_4_characters)
      else -> stringResource(R.string.PinCreationScreen__pin_at_least_4_digits)
    },
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.fillMaxWidth()
  )
}

@Composable
private fun KeyboardToggleButton(
  state: PinCreationState,
  onToggleKeyboard: () -> Unit,
  modifier: Modifier = Modifier
) {
  TextButton(
    onClick = onToggleKeyboard,
    modifier = modifier.fillMaxWidth()
  ) {
    Icon(
      painter = SignalIcons.Keyboard.painter,
      contentDescription = null,
      modifier = Modifier.padding(end = 8.dp)
    )
    Text(
      text = if (state.isAlphanumericKeyboard) {
        stringResource(R.string.PinCreationScreen__switch_to_numeric)
      } else {
        stringResource(R.string.PinCreationScreen__switch_to_alphanumeric)
      }
    )
  }
}

@Composable
private fun NextButton(
  params: RegistrationScaffold.Params,
  canSubmitPin: Boolean,
  showElevation: Boolean,
  onNext: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shadowElevation = if (showElevation) 8.dp else 0.dp
  ) {
    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier.fillMaxWidth()
    ) {
      Buttons.LargeTonal(
        onClick = onNext,
        enabled = canSubmitPin,
        modifier = Modifier
          .widthIn(max = params.maxButtonWidth)
          .padding(params.footerPadding)
      ) {
        Text(stringResource(R.string.PinCreationScreen__next))
      }
    }
  }
}

@AllDevicePreviews
@Composable
private fun PinCreationScreenNumericPreview() {
  Previews.Preview {
    PinCreationScreen(
      state = PinCreationState(
        isAlphanumericKeyboard = false
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PinCreationScreenAlphanumericPreview() {
  Previews.Preview {
    PinCreationScreen(
      state = PinCreationState(
        isAlphanumericKeyboard = true
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PinCreationScreenConfirmPreview() {
  Previews.Preview {
    PinCreationScreen(
      state = PinCreationState(isConfirmEnabled = true),
      onEvent = {}
    )
  }
}
