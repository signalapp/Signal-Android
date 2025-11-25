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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons

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
  var pin by remember { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }
  val scrollState = rememberScrollState()

  Box(
    modifier = modifier.fillMaxSize()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top
    ) {
      Spacer(modifier = Modifier.height(32.dp))

      Text(
        text = "Enter your PIN",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Enter the PIN you created when you first installed Signal",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp)
      )

      Spacer(modifier = Modifier.height(16.dp))

      TextField(
        value = pin,
        onValueChange = { pin = it },
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
          keyboardType = if (state.isNumericKeyboard) KeyboardType.Number else KeyboardType.Password,
          imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
          onDone = {
            if (pin.isNotEmpty()) {
              onEvent(PinEntryScreenEvents.PinEntered(pin))
            }
          }
        ),
        isError = state.errorMessage != null
      )

      if (state.errorMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = state.errorMessage,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
      } else {
        Spacer(modifier = Modifier.height(8.dp))
      }

      if (state.showNeedHelp) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = { onEvent(PinEntryScreenEvents.NeedHelp) },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Need help?")
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedButton(
        onClick = { onEvent(PinEntryScreenEvents.ToggleKeyboard) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(
          painter = SignalIcons.Keyboard.painter,
          contentDescription = null,
          modifier = Modifier.padding(end = 8.dp)
        )
        Text("Switch keyboard")
      }

      Spacer(modifier = Modifier.weight(1f))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        Button(
          onClick = {
            if (pin.isNotEmpty()) {
              onEvent(PinEntryScreenEvents.PinEntered(pin))
            }
          },
          enabled = pin.isNotEmpty()
        ) {
          Text("Continue")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }

    // Skip button in top right
    TextButton(
      onClick = { onEvent(PinEntryScreenEvents.Skip) },
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(8.dp)
    ) {
      Text(
        text = "Skip",
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  // Auto-focus PIN field on initial composition
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}

@DayNightPreviews
@Composable
private fun PinEntryScreenPreview() {
  Previews.Preview {
    PinEntryScreen(
      state = PinEntryState(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PinEntryScreenWithErrorPreview() {
  Previews.Preview {
    PinEntryScreen(
      state = PinEntryState(
        errorMessage = "Incorrect PIN. Try again.",
        showNeedHelp = true
      ),
      onEvent = {}
    )
  }
}
