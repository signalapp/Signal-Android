/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restore

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.QrCode
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.ui.compose.SignalIcons

/**
 * Screen to display QR code for restoring from an old device.
 * The old device scans this QR code to initiate the transfer.
 */
@Composable
fun RestoreViaQrScreen(
  state: RestoreViaQrState,
  onEvent: (RestoreViaQrScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Scan from old device",
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(32.dp))

    // QR Code display area
    Box(
      modifier = Modifier
        .widthIn(max = 280.dp)
        .aspectRatio(1f)
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surface)
          .padding(16.dp),
        contentAlignment = Alignment.Center
      ) {
        AnimatedContent(
          targetState = state.qrState,
          contentKey = { it::class },
          label = "qr-code-state"
        ) { qrState ->
          when (qrState) {
            is QrState.Loaded -> {
              QrCode(
                data = qrState.qrCodeData,
                foregroundColor = Color(0xFF2449C0),
                modifier = Modifier.fillMaxSize()
              )
            }

            QrState.Loading -> {
              CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }

            QrState.Scanned -> {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Text(
                  text = "QR code scanned",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { onEvent(RestoreViaQrScreenEvents.RetryQrCode) }) {
                  Text("Retry")
                }
              }
            }

            QrState.Failed -> {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Text(
                  text = "Failed to generate QR code",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.error,
                  textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { onEvent(RestoreViaQrScreenEvents.RetryQrCode) }) {
                  Text("Retry")
                }
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Instructions
    Column(
      modifier = Modifier.widthIn(max = 320.dp)
    ) {
      InstructionRow(
        icon = SignalIcons.Phone.painter,
        instruction = "On your old phone, open Signal"
      )

      InstructionRow(
        icon = SignalIcons.Camera.painter,
        instruction = "Go to Settings > Transfer account"
      )

      InstructionRow(
        icon = SignalIcons.QrCode.painter,
        instruction = "Scan this QR code"
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    TextButton(
      onClick = { onEvent(RestoreViaQrScreenEvents.Cancel) }
    ) {
      Text("Cancel")
    }

    Spacer(modifier = Modifier.height(16.dp))
  }

  // Loading dialog
  if (state.isRegistering) {
    AlertDialog(
      onDismissRequest = { },
      confirmButton = { },
      text = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier = Modifier.fillMaxWidth()
        ) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.width(16.dp))
          Text("Registering...")
        }
      }
    )
  }

  // Error dialog
  if (state.showRegistrationError) {
    AlertDialog(
      onDismissRequest = { onEvent(RestoreViaQrScreenEvents.DismissError) },
      confirmButton = {
        TextButton(onClick = { onEvent(RestoreViaQrScreenEvents.DismissError) }) {
          Text("OK")
        }
      },
      text = {
        Text(state.errorMessage ?: "An error occurred during registration")
      }
    )
  }
}

@Composable
private fun InstructionRow(
  icon: Painter,
  instruction: String
) {
  Row(
    modifier = Modifier.padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.width(16.dp))

    Text(
      text = instruction,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreViaQrScreenLoadingPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrState(qrState = QrState.Loading),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreViaQrScreenLoadedPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrState(
        qrState = QrState.Loaded(QrCodeData.forData("sgnl://rereg?uuid=test&pub_key=test", false))
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreViaQrScreenFailedPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrState(qrState = QrState.Failed),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreViaQrScreenRegisteringPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrState(
        qrState = QrState.Scanned,
        isRegistering = true
      ),
      onEvent = {}
    )
  }
}
