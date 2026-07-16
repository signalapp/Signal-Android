/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.main

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
      .verticalScroll(rememberScrollState())
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

    if (state.pendingFlowState != null) {
      PendingFlowStateCard(state.pendingFlowState)
      Spacer(modifier = Modifier.height(16.dp))

      Button(
        onClick = { onEvent(MainScreenEvents.LaunchRegistration) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Resume Registration")
      }

      TextButton(
        onClick = { showClearDataDialog = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(
          contentColor = MaterialTheme.colorScheme.error
        )
      ) {
        Text("Clear Pending Data")
      }

      Spacer(modifier = Modifier.height(16.dp))
    }

    if (state.existingRegistrationState != null) {
      if (state.registrationExpired) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "No longer registered. Your credentials are no longer valid on the server.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
      }

      RegistrationInfo(state.existingRegistrationState)

      if (state.linkedDeviceState != null) {
        Spacer(modifier = Modifier.height(16.dp))
        LinkedDeviceInfo(state.linkedDeviceState)
      }

      if (state.profileState != null) {
        Spacer(modifier = Modifier.height(16.dp))
        ProfileInfo(state.profileState)
      }

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = { onEvent(MainScreenEvents.LaunchRegistration) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Re-register")
      }

      OutlinedButton(
        onClick = { onEvent(MainScreenEvents.TransferAccount) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Transfer to New Device")
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
    } else if (state.pendingFlowState == null) {
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
      RegistrationField(label = "Restore Decision", value = data.restoreDecision ?: "(unset)")
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
private fun LinkedDeviceInfo(data: MainScreenState.LinkedDeviceState) {
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
        text = "Linked Device",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      RegistrationField(label = "Device Type", value = "Linked (secondary)")
      RegistrationField(label = "Device ID", value = data.deviceId.toString())
      RegistrationField(label = "Link & Sync Offered", value = if (data.linkAndSyncOffered) "Yes" else "No")
      RegistrationField(
        label = "Link & Sync Backup",
        value = when {
          !data.linkAndSyncOffered -> "Not offered by primary"
          data.linkAndSyncFrameCount >= 0 -> "Downloaded ${data.linkAndSyncDownloadedBytes} bytes, ${data.linkAndSyncFrameCount} frames (not imported)"
          else -> "Offered, not completed"
        }
      )
    }
  }
}

@Composable
private fun ProfileInfo(profile: MainScreenState.ProfileState) {
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
        text = "Profile",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      RegistrationField(label = "Given Name", value = profile.givenName.ifEmpty { "(not set)" })
      RegistrationField(label = "Family Name", value = profile.familyName.ifEmpty { "(not set)" })
      RegistrationField(
        label = "Avatar",
        value = profile.avatarSizeBytes?.let { "$it bytes" } ?: "(not set)"
      )
      RegistrationField(
        label = "Discoverable by Phone Number",
        value = when (profile.discoverableByPhoneNumber) {
          true -> "Yes"
          false -> "No"
          null -> "(undecided)"
        }
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RegistrationField(label: String, value: String) {
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = {},
        onLongClick = {
          clipboardManager.setText(AnnotatedString(value))
          Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
        }
      )
      .padding(vertical = 4.dp)
  ) {
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

@Composable
private fun PendingFlowStateCard(pending: MainScreenState.PendingFlowState) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.tertiaryContainer
    )
  ) {
    Column(
      modifier = Modifier.padding(16.dp)
    ) {
      Text(
        text = "In-Progress Registration",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onTertiaryContainer
      )

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      RegistrationField(label = "Current Screen", value = pending.currentScreen)
      RegistrationField(label = "Backstack Depth", value = pending.backstackSize.toString())
      if (pending.e164 != null) {
        RegistrationField(label = "Phone Number", value = pending.e164)
      }
      RegistrationField(label = "Has Session", value = if (pending.hasSession) "Yes" else "No")
      RegistrationField(label = "Has AEP", value = if (pending.hasAccountEntropyPool) "Yes" else "No")
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenWithPendingFlowStatePreview() {
  Previews.Preview {
    MainScreen(
      state = MainScreenState(
        pendingFlowState = MainScreenState.PendingFlowState(
          e164 = "+15551234567",
          backstackSize = 4,
          currentScreen = "VerificationCodeEntry",
          hasSession = true,
          hasAccountEntropyPool = false
        )
      ),
      onEvent = {}
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
          temporaryMasterKey = null,
          restoreDecision = "COMPLETED"
        ),
        profileState = MainScreenState.ProfileState(
          givenName = "Ada",
          familyName = "Lovelace",
          avatarSizeBytes = 12_345,
          discoverableByPhoneNumber = true
        )
      ),
      onEvent = {}
    )
  }
}
