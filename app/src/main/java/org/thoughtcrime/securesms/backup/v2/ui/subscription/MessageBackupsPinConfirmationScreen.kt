/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType

/**
 * Screen which requires the user to enter their pin before enabling backups.
 */
@Composable
fun MessageBackupsPinConfirmationScreen(
  pin: String,
  onPinChanged: (String) -> Unit,
  pinKeyboardType: PinKeyboardType,
  onPinKeyboardTypeSelected: (PinKeyboardType) -> Unit,
  onNextClick: () -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  Surface {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        item {
          Text(
            text = stringResource(id = R.string.MessageBackupsPinConfirmationScreen__enter_your_pin),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 40.dp)
          )
        }

        item {
          Text(
            text = stringResource(id = R.string.MessageBackupsPinConfirmationScreen__enter_your_signal_pin_to_enable_backups),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
          )
        }

        item {
          val keyboardType = remember(pinKeyboardType) {
            when (pinKeyboardType) {
              PinKeyboardType.NUMERIC -> KeyboardType.NumberPassword
              PinKeyboardType.ALPHA_NUMERIC -> KeyboardType.Password
            }
          }

          TextField(
            value = pin,
            onValueChange = onPinChanged,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardActions = KeyboardActions(
              onDone = { onNextClick() }
            ),
            keyboardOptions = KeyboardOptions(
              keyboardType = keyboardType,
              imeAction = ImeAction.Done
            ),
            modifier = Modifier
              .padding(top = 72.dp)
              .fillMaxWidth()
              .focusRequester(focusRequester),
            visualTransformation = PasswordVisualTransformation()
          )
        }

        item {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 48.dp)
          ) {
            PinKeyboardTypeToggle(
              pinKeyboardType = pinKeyboardType,
              onPinKeyboardTypeSelected = onPinKeyboardTypeSelected
            )
          }
        }
      }

      Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 16.dp)
      ) {
        Buttons.LargeTonal(
          onClick = onNextClick
        ) {
          Text(
            text = stringResource(id = R.string.MessageBackupsPinConfirmationScreen__next)
          )
        }
      }

      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }
    }
  }
}

@Preview
@Composable
private fun MessageBackupsPinConfirmationScreenPreview() {
  Previews.Preview {
    MessageBackupsPinConfirmationScreen(
      pin = "",
      onPinChanged = {},
      pinKeyboardType = PinKeyboardType.ALPHA_NUMERIC,
      onPinKeyboardTypeSelected = {},
      onNextClick = {}
    )
  }
}

@Preview
@Composable
private fun PinKeyboardTypeTogglePreview() {
  Previews.Preview {
    var type by remember { mutableStateOf(PinKeyboardType.ALPHA_NUMERIC) }
    PinKeyboardTypeToggle(
      pinKeyboardType = type,
      onPinKeyboardTypeSelected = { type = it }
    )
  }
}

@Composable
private fun PinKeyboardTypeToggle(
  pinKeyboardType: PinKeyboardType,
  onPinKeyboardTypeSelected: (PinKeyboardType) -> Unit
) {
  val callback = remember(pinKeyboardType) {
    { onPinKeyboardTypeSelected(pinKeyboardType.other) }
  }

  val iconRes = remember(pinKeyboardType) {
    when (pinKeyboardType) {
      PinKeyboardType.NUMERIC -> R.drawable.symbol_keyboard_24
      PinKeyboardType.ALPHA_NUMERIC -> R.drawable.symbol_number_pad_24
    }
  }

  TextButton(onClick = callback) {
    Icon(
      painter = painterResource(id = iconRes),
      tint = MaterialTheme.colorScheme.primary,
      contentDescription = null,
      modifier = Modifier.padding(end = 8.dp)
    )
    Text(
      text = stringResource(id = R.string.MessageBackupsPinConfirmationScreen__switch_keyboard)
    )
  }
}
