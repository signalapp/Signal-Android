/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.devicetransfer.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.Texts
import org.signal.devicetransfer.TransferStatus.TransferMode
import org.signal.devicetransfer.WifiDirect
import org.signal.devicetransfer.app.DeviceTransferTestState.Dialog
import org.signal.devicetransfer.app.DeviceTransferTestState.PayloadSize
import org.signal.devicetransfer.app.DeviceTransferTestState.Progress
import org.signal.devicetransfer.app.DeviceTransferTestState.Readiness
import org.signal.devicetransfer.app.DeviceTransferTestState.Role
import org.signal.devicetransfer.app.DeviceTransferTestState.TransferResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceTransferTestScreen(
  state: DeviceTransferTestState,
  onEvent: (DeviceTransferTestEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = "Device transfer test",
    onNavigationClick = {},
    modifier = modifier
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier.padding(contentPadding)
    ) {
      deviceSection(state)
      readinessSection(state.readiness, onEvent)
      payloadSection(state, onEvent)
      controlsSection(state, onEvent)
      statusSection(state)
      logSection(state, onEvent)
    }

    when (val dialog = state.dialog) {
      Dialog.None -> Unit
      is Dialog.Verification -> VerificationDialog(dialog, onEvent)
    }
  }
}

private fun LazyListScope.deviceSection(state: DeviceTransferTestState) {
  item {
    Texts.SectionHeader(text = "Device")
    Rows.TextRow(text = state.deviceDescription, label = state.libsignalVersion)
  }
}

private fun LazyListScope.readinessSection(readiness: Readiness, onEvent: (DeviceTransferTestEvent) -> Unit) {
  item {
    Dividers.Default()
    Texts.SectionHeader(text = "Readiness")

    Rows.TextRow(
      text = "Wi-Fi Direct",
      label = readiness.availability?.name ?: "-"
    )

    val permissionLabel = if (readiness.usesNearbyPermission) {
      "Nearby Wi-Fi devices permission"
    } else {
      "Fine location permission"
    }
    GrantedRow(permissionLabel, readiness.wifiDirectPermissionGranted)
    GrantedRow("Notification permission", readiness.notificationPermissionGranted)
    YesNoRow("Wi-Fi enabled", readiness.wifiEnabled)
    YesNoRow("Location services enabled", readiness.locationEnabled)

    if (readiness.hasMissingPermissions) {
      Buttons.MediumTonal(
        onClick = { onEvent(DeviceTransferTestEvent.GrantPermissionsClicked) },
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
      ) {
        Text(text = "Grant permissions")
      }
    }
  }
}

private fun LazyListScope.payloadSection(state: DeviceTransferTestState, onEvent: (DeviceTransferTestEvent) -> Unit) {
  item {
    Dividers.Default()
    Texts.SectionHeader(text = "Payload sent by the client")
  }

  items(PayloadSize.entries, key = { it.name }) { size ->
    Rows.RadioRow(
      selected = state.payloadSize == size,
      text = "${size.bytes / BYTES_PER_MB} MB",
      enabled = !state.isRunning,
      modifier = Modifier
        .selectable(
          selected = state.payloadSize == size,
          enabled = !state.isRunning,
          onClick = { onEvent(DeviceTransferTestEvent.PayloadSizeSelected(size)) }
        )
    )
  }
}

private fun LazyListScope.controlsSection(state: DeviceTransferTestState, onEvent: (DeviceTransferTestEvent) -> Unit) {
  item {
    Dividers.Default()
    Texts.SectionHeader(text = "Controls")

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
      ControlButton("Start server (receive, new device)", state.canStart) {
        onEvent(DeviceTransferTestEvent.StartServerClicked)
      }
      ControlButton("Start client (send, old device)", state.canStart) {
        onEvent(DeviceTransferTestEvent.StartClientClicked)
      }
      ControlButton("Restart (stop then start, like Retry)", state.isRunning) {
        onEvent(DeviceTransferTestEvent.RestartClicked)
      }
      ControlButton("Stop", enabled = true) {
        onEvent(DeviceTransferTestEvent.StopClicked)
      }
    }
  }
}

private fun LazyListScope.statusSection(state: DeviceTransferTestState) {
  item {
    Dividers.Default()
    Texts.SectionHeader(text = "Status")

    val role = when (state.role) {
      Role.NONE -> "Idle"
      Role.SERVER -> "Server"
      Role.CLIENT -> "Client"
    }
    Rows.TextRow(text = "Role", label = role)
    Rows.TextRow(
      text = "Transfer status",
      label = state.transferMode?.name ?: "None yet"
    )

    state.progress?.let { ProgressRow(it) }
    state.result?.let { ResultRow(it) }
  }
}

private fun LazyListScope.logSection(state: DeviceTransferTestState, onEvent: (DeviceTransferTestEvent) -> Unit) {
  item {
    Dividers.Default()
    Texts.SectionHeader(
      text = "Log",
      trailingContent = {
        TextButton(
          onClick = { onEvent(DeviceTransferTestEvent.ClearLogClicked) }
        ) {
          Text(text = "Clear")
        }
      }
    )

    if (state.log.isEmpty()) {
      Text(
        text = "Nothing logged yet",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
      )
    }
  }

  items(state.log.asReversed()) { entry ->
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    Text(
      text = "${formatter.format(Date(entry.timestampMillis))}  ${entry.message}",
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
    )
  }
}

@Composable
private fun GrantedRow(text: String, granted: Boolean) {
  Rows.TextRow(
    text = text,
    label = if (granted) "Granted" else "Missing"
  )
}

@Composable
private fun YesNoRow(text: String, value: Boolean) {
  Rows.TextRow(
    text = text,
    label = if (value) "Yes" else "No"
  )
}

@Composable
private fun ControlButton(text: String, enabled: Boolean, onClick: () -> Unit) {
  Buttons.LargeTonal(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
  ) {
    Text(text = text)
  }
}

@Composable
private fun ProgressRow(progress: Progress) {
  Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
    LinearProgressIndicator(
      progress = { progress.fraction },
      modifier = Modifier.fillMaxWidth()
    )
    Text(
      text = "${formatMb(progress.bytes)} of ${formatMb(progress.totalBytes)} MB at ${formatMb(progress.bytesPerSecond)} MB/s",
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(top = 4.dp)
    )
  }
}

@Composable
private fun ResultRow(result: TransferResult) {
  val text = when (result) {
    is TransferResult.Sent -> "Sent ${formatMb(result.bytes)} MB in ${formatSeconds(result.durationMillis)} s"
    is TransferResult.Received -> {
      val checksum = if (result.checksumMatches) "checksum matches" else "CHECKSUM MISMATCH"
      "Received ${formatMb(result.bytes)} MB in ${formatSeconds(result.durationMillis)} s, $checksum"
    }
    is TransferResult.Failed -> "Transfer failed: ${result.message}"
  }

  val isError = result is TransferResult.Failed || (result is TransferResult.Received && !result.checksumMatches)

  Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
  )
}

@Composable
private fun VerificationDialog(dialog: Dialog.Verification, onEvent: (DeviceTransferTestEvent) -> Unit) {
  Dialogs.SimpleAlertDialog(
    title = "Verify code",
    body = "Does the other device show ${"%07d".format(Locale.US, dialog.code)}?",
    confirm = "Yes, same",
    dismiss = "No, different",
    onConfirm = { onEvent(DeviceTransferTestEvent.VerificationAnswered(codesMatch = true)) },
    onDeny = { onEvent(DeviceTransferTestEvent.VerificationAnswered(codesMatch = false)) },
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
  )
}

private const val BYTES_PER_MB = 1024L * 1024L

private fun formatMb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes.toDouble() / BYTES_PER_MB)

private fun formatSeconds(millis: Long): String = String.format(Locale.US, "%.1f", millis / 1000.0)

@DayNightPreviews
@Composable
private fun DeviceTransferTestScreenIdlePreview() {
  Previews.Preview {
    DeviceTransferTestScreen(
      state = DeviceTransferTestState(
        deviceDescription = "Google Pixel 10, Android 17 (API 37)",
        libsignalVersion = "libsignal 0.80.0",
        readiness = Readiness(availability = WifiDirect.AvailableStatus.REQUIRED_PERMISSION_NOT_GRANTED, wifiEnabled = true)
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceTransferTestScreenTransferringPreview() {
  Previews.Preview {
    DeviceTransferTestScreen(
      state = DeviceTransferTestState(
        deviceDescription = "Google Pixel 3a, Android 15 (API 35)",
        libsignalVersion = "libsignal 0.80.0",
        readiness = Readiness(
          availability = WifiDirect.AvailableStatus.AVAILABLE,
          wifiDirectPermissionGranted = true,
          notificationPermissionGranted = true,
          wifiEnabled = true,
          locationEnabled = true
        ),
        role = Role.SERVER,
        transferMode = TransferMode.SERVICE_CONNECTED,
        progress = Progress(bytes = 96L * BYTES_PER_MB, totalBytes = 256L * BYTES_PER_MB, elapsedMillis = 8_000),
        log = listOf(
          DeviceTransferTestState.LogEntry(0, "Start server"),
          DeviceTransferTestState.LogEntry(1_000, "Status: SERVICE_CONNECTED")
        )
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceTransferTestScreenVerificationPreview() {
  Previews.Preview {
    DeviceTransferTestScreen(
      state = DeviceTransferTestState(
        role = Role.CLIENT,
        transferMode = TransferMode.VERIFICATION_REQUIRED,
        dialog = Dialog.Verification(code = 1234567)
      ),
      onEvent = {}
    )
  }
}
