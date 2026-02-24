/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons

/**
 * PIN creation screen for the registration flow.
 * Allows users to create a new PIN for their account.
 */
@Composable
fun PinCreationScreen(
  state: PinCreationState,
  onEvent: (PinCreationScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var pin by remember { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Top
  ) {
    Spacer(modifier = Modifier.height(32.dp))

    Text(
      text = "Create your PIN",
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Start,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    val descriptionText = buildAnnotatedString {
      append("PINs can help you restore your account if you lose your phone. ")
      pushStringAnnotation(tag = "LEARN_MORE", annotation = "learn_more")
      withStyle(
        style = SpanStyle(
          color = MaterialTheme.colorScheme.primary,
          textDecoration = TextDecoration.Underline
        )
      ) {
        append("Learn more")
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
          .firstOrNull()?.let {
            onEvent(PinCreationScreenEvents.LearnMore)
          }
      }
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
        keyboardType = if (state.isAlphanumericKeyboard) KeyboardType.Password else KeyboardType.NumberPassword,
        imeAction = ImeAction.Done
      ),
      keyboardActions = KeyboardActions(
        onDone = {
          if (pin.length >= 4) {
            onEvent(PinCreationScreenEvents.PinSubmitted(pin))
          }
        }
      )
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = state.inputLabel ?: "",
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
      onClick = { onEvent(PinCreationScreenEvents.ToggleKeyboard) },
      modifier = Modifier.fillMaxWidth()
    ) {
      Icon(
        painter = SignalIcons.Keyboard.painter,
        contentDescription = null,
        modifier = Modifier.padding(end = 8.dp)
      )
      Text(
        text = if (state.isAlphanumericKeyboard) "Switch to numeric" else "Switch to alphanumberic"
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End
    ) {
      Button(
        onClick = { onEvent(PinCreationScreenEvents.PinSubmitted(pin)) },
        enabled = pin.length >= 4
      ) {
        Text("Next")
      }
    }

    Spacer(modifier = Modifier.height(16.dp))
  }

  // Auto-focus PIN field on initial composition
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}

@DayNightPreviews
@Composable
private fun PinCreationScreenPreview() {
  Previews.Preview {
    PinCreationScreen(
      state = PinCreationState(
        inputLabel = "PIN must be at least 4 digits"
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PinCreationScreenAlphanumericPreview() {
  Previews.Preview {
    PinCreationScreen(
      state = PinCreationState(
        isAlphanumericKeyboard = false,
        inputLabel = "PIN must be at least 4 characters"
      ),
      onEvent = {}
    )
  }
}
