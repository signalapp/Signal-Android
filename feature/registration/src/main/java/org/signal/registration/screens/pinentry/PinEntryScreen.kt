/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
 * PIN entry screen for the registration flow.
 * Allows users to enter their PIN to restore their account.
 */
@Composable
fun PinEntryScreen(
  state: PinEntryState,
  onEvent: (PinEntryScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var pin by rememberSaveable { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }
  val canSubmitPin = pin.isNotEmpty()

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
  state: PinEntryState,
  pin: String,
  canSubmitPin: Boolean,
  focusRequester: FocusRequester,
  onPinChanged: (String) -> Unit,
  onEvent: (PinEntryScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  RegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    content = {
      Box(
        modifier = modifier.fillMaxSize()
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(params.panePadding(hasHeader = false))
        ) {
          PinDescription(
            mode = state.mode,
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(24.dp))

          PinInputField(
            state = state,
            pin = pin,
            canSubmitPin = canSubmitPin,
            focusRequester = focusRequester,
            onPinChanged = onPinChanged,
            onSubmit = { onEvent(PinEntryScreenEvents.PinEntered(pin)) },
            onNeedsHelp = { onEvent(PinEntryScreenEvents.NeedHelp) },
            modifier = Modifier.fillMaxWidth()
          )

          KeyboardToggleButton(
            onToggleKeyboard = { onEvent(PinEntryScreenEvents.ToggleKeyboard) }
          )
        }

        SkipButton(
          onSkip = { onEvent(PinEntryScreenEvents.Skip) },
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(params.edgeInset)
        )
      }
    },
    footer = {
      ContinueButton(
        params = params,
        canSubmitPin = canSubmitPin,
        isElevated = scrollState.canScrollForward,
        onContinue = { onEvent(PinEntryScreenEvents.PinEntered(pin)) }
      )
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: PinEntryState,
  pin: String,
  canSubmitPin: Boolean,
  focusRequester: FocusRequester,
  onPinChanged: (String) -> Unit,
  onEvent: (PinEntryScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    params = params,
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        PinDescription(
          mode = state.mode,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    secondPane = { paddingValues ->
      Box(
        modifier = modifier
          .weight(1f)
          .fillMaxHeight()
      ) {
        Column(
          modifier = Modifier
            .verticalScroll(secondPaneScrollState)
            .padding(paddingValues)
        ) {
          PinInputField(
            state = state,
            pin = pin,
            canSubmitPin = canSubmitPin,
            focusRequester = focusRequester,
            onPinChanged = onPinChanged,
            onSubmit = { onEvent(PinEntryScreenEvents.PinEntered(pin)) },
            onNeedsHelp = { onEvent(PinEntryScreenEvents.NeedHelp) },
            modifier = Modifier.fillMaxWidth()
          )

          KeyboardToggleButton(
            onToggleKeyboard = { onEvent(PinEntryScreenEvents.ToggleKeyboard) }
          )
        }

        SkipButton(
          onSkip = { onEvent(PinEntryScreenEvents.Skip) },
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(params.edgeInset)
        )
      }
    },
    footer = {
      ContinueButton(
        params = params,
        canSubmitPin = canSubmitPin,
        isElevated = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward,
        onContinue = { onEvent(PinEntryScreenEvents.PinEntered(pin)) }
      )
    }
  )
}

@Composable
private fun PinDescription(
  mode: PinEntryState.Mode,
  modifier: Modifier = Modifier
) {
  val titleString = when (mode) {
    PinEntryState.Mode.RegistrationLock -> stringResource(R.string.PinEntryScreen__registration_lock)
    PinEntryState.Mode.SvrRestore,
    PinEntryState.Mode.SmsBypass -> stringResource(R.string.PinEntryScreen__enter_your_pin)
  }

  Column(modifier = modifier) {
    Text(
      text = titleString,
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .attachDebugLogHelper()
    )

    Text(
      text = stringResource(R.string.PinEntryScreen__enter_the_pin_you_created),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Start,
      modifier = Modifier.padding(top = 16.dp)
    )
  }
}

@Composable
private fun PinInputField(
  state: PinEntryState,
  pin: String,
  canSubmitPin: Boolean,
  focusRequester: FocusRequester,
  onPinChanged: (String) -> Unit,
  onSubmit: () -> Unit,
  onNeedsHelp: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    TextField(
      value = pin,
      onValueChange = onPinChanged,
      modifier = Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester),
      textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
      singleLine = true,
      keyboardOptions = KeyboardOptions(
        keyboardType = if (state.isAlphanumericKeyboard) KeyboardType.Text else KeyboardType.Number,
        imeAction = ImeAction.Done
      ),
      keyboardActions = KeyboardActions(onDone = { if (canSubmitPin) onSubmit() }),
      isError = state.triesRemaining != null,
      visualTransformation = PasswordVisualTransformation()
    )

    if (state.triesRemaining != null) {
      Spacer(modifier = Modifier.height(8.dp))
      PinInputLabel(
        text = pluralStringResource(R.plurals.PinEntryScreen__incorrect_pin, state.triesRemaining, state.triesRemaining),
        isError = true
      )
    } else {
      Spacer(modifier = Modifier.height(8.dp))
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (state.showNeedHelp) {
      OutlinedButton(
        onClick = onNeedsHelp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(stringResource(R.string.PinEntryScreen__need_help))
      }
    }
  }
}

@Composable
private fun PinInputLabel(
  text: String,
  isError: Boolean,
  modifier: Modifier = Modifier
) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = modifier.fillMaxWidth()
  )
}

@Composable
private fun KeyboardToggleButton(
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
    Text(stringResource(R.string.PinEntryScreen__switch_keyboard))
  }
}

@Composable
private fun ContinueButton(
  params: RegistrationScaffold.Params,
  canSubmitPin: Boolean,
  isElevated: Boolean,
  onContinue: () -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScaffold.FooterSurface(
    isElevated = isElevated,
    modifier = modifier
  ) {
    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier.fillMaxWidth()
    ) {
      Buttons.LargeTonal(
        onClick = onContinue,
        enabled = canSubmitPin,
        modifier = Modifier
          .widthIn(max = params.maxButtonWidth)
          .padding(params.footerPadding)
      ) {
        Text(stringResource(R.string.PinEntryScreen__continue))
      }
    }
  }
}

@Composable
private fun SkipButton(
  onSkip: () -> Unit,
  modifier: Modifier = Modifier
) {
  TextButton(
    onClick = onSkip,
    modifier = modifier
  ) {
    Text(
      text = stringResource(R.string.PinEntryScreen__skip),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@AllDevicePreviews
@Composable
private fun PinEntryScreenPreview() {
  Previews.Preview {
    PinEntryScreen(
      state = PinEntryState(),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PinEntryScreenWithErrorPreview() {
  Previews.Preview {
    PinEntryScreen(
      state = PinEntryState(
        mode = PinEntryState.Mode.RegistrationLock,
        triesRemaining = 3,
        showNeedHelp = true
      ),
      onEvent = {}
    )
  }
}
