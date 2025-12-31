/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.pinsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSettingsScreen(
  state: PinSettingsState,
  onEvent: (PinSettingsEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var pinInput by remember { mutableStateOf("") }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("PIN Settings") },
        navigationIcon = {
          TextButton(onClick = { onEvent(PinSettingsEvents.Back) }) {
            Text("Back")
          }
        }
      )
    },
    snackbarHost = {
      if (state.toastMessage != null) {
        Snackbar(
          action = {
            TextButton(onClick = { onEvent(PinSettingsEvents.DismissMessage) }) {
              Text("Dismiss")
            }
          }
        ) {
          Text(state.toastMessage)
        }
      }
    },
    modifier = modifier
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
      ) {
        // PIN Setup Section
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        ) {
          Column(
            modifier = Modifier.padding(16.dp)
          ) {
            Text(
              text = "Set Your PIN",
              style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Your PIN protects your account and allows you to restore your data if you need to re-register.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
              value = pinInput,
              onValueChange = { if (it.length <= 6) pinInput = it },
              label = { Text("Enter PIN (4-6 digits)") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
              ),
              keyboardActions = KeyboardActions(
                onDone = {
                  if (pinInput.length >= 4 && !state.pinsOptedOut) {
                    onEvent(PinSettingsEvents.SetPin(pinInput))
                  }
                }
              ),
              enabled = !state.loading && !state.pinsOptedOut
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
              onClick = { onEvent(PinSettingsEvents.SetPin(pinInput)) },
              modifier = Modifier.fillMaxWidth(),
              enabled = pinInput.length >= 4 && !state.loading && !state.pinsOptedOut
            ) {
              Text(if (state.hasPinSet) "Update PIN" else "Set PIN")
            }

            if (state.hasPinSet && !state.pinsOptedOut) {
              Text(
                text = "PIN is currently set",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
              )
            }

            if (state.pinsOptedOut) {
              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
              Text(
                text = "Opt back into PINs to set a PIN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Registration Lock Section
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        ) {
          Column(
            modifier = Modifier.padding(16.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Registration Lock",
                  style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                  text = "When enabled, your PIN will be required when re-registering your phone number.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              Switch(
                checked = state.registrationLockEnabled,
                onCheckedChange = { onEvent(PinSettingsEvents.ToggleRegistrationLock) },
                enabled = state.hasPinSet && !state.loading && !state.pinsOptedOut
              )
            }

            if (!state.hasPinSet && !state.pinsOptedOut) {
              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
              Text(
                text = "Set a PIN first to enable registration lock",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }

            if (state.pinsOptedOut) {
              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
              Text(
                text = "Opt back into PINs to enable registration lock",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PIN Opt-Out Section
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        ) {
          Column(
            modifier = Modifier.padding(16.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Opt Out of PINs",
                  style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                  text = "Disables PIN-based account recovery. Your data will not be backed up to the server.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              Switch(
                checked = state.pinsOptedOut,
                onCheckedChange = { onEvent(PinSettingsEvents.TogglePinsOptOut) },
                enabled = !state.registrationLockEnabled && !state.loading
              )
            }

            if (state.registrationLockEnabled) {
              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
              Text(
                text = "Disable registration lock first to opt out of PINs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }

      if (state.loading) {
        Dialogs.IndeterminateProgressDialog()
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun PinSettingsScreenPreview() {
  Previews.Preview {
    PinSettingsScreen(
      state = PinSettingsState(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PinSettingsScreenWithPinPreview() {
  Previews.Preview {
    PinSettingsScreen(
      state = PinSettingsState(
        hasPinSet = true,
        registrationLockEnabled = true
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PinSettingsScreenLoadingPreview() {
  Previews.Preview {
    PinSettingsScreen(
      state = PinSettingsState(
        loading = true
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PinSettingsScreenOptedOutPreview() {
  Previews.Preview {
    PinSettingsScreen(
      state = PinSettingsState(
        pinsOptedOut = true
      ),
      onEvent = {}
    )
  }
}
