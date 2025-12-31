/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Previews

@Composable
fun MainScreen(
  state: MainScreenState,
  onEvent: (MainScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var showClearDataDialog by remember { mutableStateOf(false) }

  if (showClearDataDialog) {
    AlertDialog(
      onDismissRequest = { showClearDataDialog = false },
      title = { Text("Clear All Data?") },
      text = { Text("This will delete all registration data including your account information, keys, and PIN. This cannot be undone.") },
      confirmButton = {
        TextButton(
          onClick = {
            showClearDataDialog = false
            onEvent(MainScreenEvents.ClearAllData)
          },
          colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          )
        ) {
          Text("Clear")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDataDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Spacer(modifier = Modifier.height(32.dp))

    if (state.existingRegistrationState == null) {
      Text(
        text = "Registration Sample App",
        style = MaterialTheme.typography.headlineMedium
      )

      Text(
        text = "Test the registration flow",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 8.dp)
      )

      Spacer(modifier = Modifier.height(32.dp))
    }

    if (state.existingRegistrationState != null) {
      RegistrationInfo(state.existingRegistrationState)

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = { onEvent(MainScreenEvents.LaunchRegistration) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Re-register")
      }

      OutlinedButton(
        onClick = { onEvent(MainScreenEvents.OpenPinSettings) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("PIN & Registration Lock Settings")
      }

      TextButton(
        onClick = { showClearDataDialog = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(
          contentColor = MaterialTheme.colorScheme.error
        )
      ) {
        Text("Clear All Data")
      }
    } else {
      Button(
        onClick = { onEvent(MainScreenEvents.LaunchRegistration) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Start Registration")
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
  }
}

@Composable
private fun RegistrationInfo(data: MainScreenState.ExistingRegistrationState) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(
      modifier = Modifier.padding(16.dp)
    ) {
      Text(
        text = "Registered Account",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      RegistrationField(label = "Phone Number", value = data.phoneNumber)
      RegistrationField(label = "ACI", value = data.aci)
      RegistrationField(label = "PNI", value = data.pni)
      RegistrationField(label = "AEP", value = data.aep)
      RegistrationField(label = "Temporary Master Key", value = data.temporaryMasterKey ?: "null")
      if (data.pinsOptedOut) {
        RegistrationField(label = "PINs Opted Out", value = "Yes")
      } else {
        RegistrationField(label = "PIN", value = data.pin ?: "(not set)")
        RegistrationField(label = "Registration Lock", value = if (data.registrationLockEnabled) "Enabled" else "Disabled")
      }
    }
  }
}

@Composable
private fun RegistrationField(label: String, value: String) {
  Column(modifier = Modifier.padding(vertical = 4.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
  Previews.Preview {
    MainScreen(
      state = MainScreenState(),
      onEvent = {}
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenWithRegistrationPreview() {
  Previews.Preview {
    MainScreen(
      state = MainScreenState(
        existingRegistrationState = MainScreenState.ExistingRegistrationState(
          phoneNumber = "+15551234567",
          aci = "12345678-1234-1234-1234-123456789abc",
          pni = "abcdefab-abcd-abcd-abcd-abcdefabcdef",
          aep = "aep1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcd",
          pin = "1234",
          registrationLockEnabled = true,
          pinsOptedOut = false,
          temporaryMasterKey = null
        )
      ),
      onEvent = {}
    )
  }
}
