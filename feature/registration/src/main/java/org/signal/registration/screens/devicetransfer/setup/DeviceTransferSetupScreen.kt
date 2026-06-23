/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration.screens.devicetransfer.setup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.devicetransfer.WifiDirect
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScaffold
import java.util.Locale

@Composable
fun DeviceTransferSetupScreen(
  state: DeviceTransferSetupState,
  onEvent: (DeviceTransferSetupScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val permissionState = rememberPermissionState(WifiDirect.requiredPermission()) { granted ->
    onEvent(if (granted) DeviceTransferSetupScreenEvents.PermissionsGranted else DeviceTransferSetupScreenEvents.PermissionsDenied)
  }

  DeviceTransferSetupScreen(
    state = state,
    permissionState = permissionState,
    onEvent = onEvent,
    modifier = modifier
  )
}

@Composable
private fun DeviceTransferSetupScreen(
  state: DeviceTransferSetupState,
  permissionState: PermissionState,
  onEvent: (DeviceTransferSetupScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  BackHandler(enabled = true) {
    onEvent(DeviceTransferSetupScreenEvents.BackClicked)
  }

  LaunchedEffect(state.oneTimeEvent) {
    val event = state.oneTimeEvent ?: return@LaunchedEffect
    onEvent(DeviceTransferSetupScreenEvents.ConsumeOneTimeEvent)
    when (event) {
      DeviceTransferSetupState.OneTimeEvent.RequestLocationPermission -> {
        when (permissionState.status) {
          is PermissionStatus.Granted -> onEvent(DeviceTransferSetupScreenEvents.PermissionsGranted)
          is PermissionStatus.Denied -> permissionState.launchPermissionRequest()
        }
      }
      DeviceTransferSetupState.OneTimeEvent.OpenLocationSettings -> {
        runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
          .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
      }
      DeviceTransferSetupState.OneTimeEvent.OpenWifiSettings -> {
        runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
          .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
      }
      DeviceTransferSetupState.OneTimeEvent.OpenAppSettings -> {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
          context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
          // nothing we can do
        }
      }
      DeviceTransferSetupState.OneTimeEvent.NavigateToProgress,
      DeviceTransferSetupState.OneTimeEvent.NavigateAway -> {
        // Navigation is handled by the ViewModel via parentEventEmitter.
      }
    }
  }

  if (state.showVerifyRejectDialog) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.DeviceTransferSetup__the_numbers_do_not_match),
      body = stringResource(R.string.DeviceTransferSetup__if_numbers_dont_match_wrong_device),
      confirm = stringResource(R.string.DeviceTransferSetup__stop_transfer),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onEvent(DeviceTransferSetupScreenEvents.VerifyRejectConfirmed) },
      onDismiss = { onEvent(DeviceTransferSetupScreenEvents.VerifyRejectDismissed) },
      confirmColor = MaterialTheme.colorScheme.error
    )
  }

  RegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    content = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        when (state.step) {
          SetupStep.INITIAL,
          SetupStep.PERMISSIONS_CHECK,
          SetupStep.LOCATION_CHECK,
          SetupStep.WIFI_CHECK,
          SetupStep.WIFI_DIRECT_CHECK,
          SetupStep.START,
          SetupStep.CONNECTED -> ProgressStep(statusText = null)

          SetupStep.SETTING_UP -> ProgressStep(
            statusText = if (state.takingTooLong) {
              stringResource(R.string.DeviceTransferSetup__take_a_moment_should_be_ready_soon)
            } else {
              stringResource(R.string.DeviceTransferSetup__preparing_to_connect)
            }
          )

          SetupStep.WAITING -> ProgressStep(
            statusText = stringResource(R.string.DeviceTransferSetup__waiting_for_old_device)
          )

          SetupStep.VERIFY -> VerifyStep(
            authenticationCode = state.authenticationCode ?: 0,
            onVerified = { onEvent(DeviceTransferSetupScreenEvents.UserVerifiedCode) },
            onRejected = { onEvent(DeviceTransferSetupScreenEvents.UserRejectedCode) }
          )

          SetupStep.WAITING_FOR_OTHER_TO_VERIFY -> ProgressStep(
            statusText = stringResource(R.string.DeviceTransferSetup__waiting_for_other_to_verify)
          )

          SetupStep.PERMISSIONS_DENIED -> ErrorStep(
            message = stringResource(R.string.DeviceTransferSetup__location_permission_required),
            buttonText = stringResource(R.string.DeviceTransferSetup__grant_location_permission),
            onButtonClick = { onEvent(DeviceTransferSetupScreenEvents.RequestPermissionClicked) }
          )

          SetupStep.LOCATION_DISABLED -> ErrorStep(
            message = stringResource(R.string.DeviceTransferSetup__location_services_required),
            buttonText = stringResource(R.string.DeviceTransferSetup__turn_on_location_services),
            onButtonClick = { onEvent(DeviceTransferSetupScreenEvents.OpenLocationSettingsClicked) }
          )

          SetupStep.WIFI_DISABLED -> ErrorStep(
            message = stringResource(R.string.DeviceTransferSetup__wifi_required),
            buttonText = stringResource(R.string.DeviceTransferSetup__turn_on_wifi),
            onButtonClick = { onEvent(DeviceTransferSetupScreenEvents.OpenWifiSettingsClicked) }
          )

          SetupStep.WIFI_DIRECT_UNAVAILABLE -> ErrorStep(
            message = stringResource(R.string.DeviceTransferSetup__wifi_direct_unavailable),
            buttonText = stringResource(R.string.DeviceTransferSetup__restore_a_backup),
            onButtonClick = { onEvent(DeviceTransferSetupScreenEvents.BackClicked) }
          )

          SetupStep.TROUBLESHOOTING -> TroubleshootingStep(onTryAgain = { onEvent(DeviceTransferSetupScreenEvents.RetryClicked) })

          SetupStep.ERROR -> ErrorStep(
            message = stringResource(R.string.DeviceTransferSetup__unexpected_error_connecting),
            buttonText = stringResource(R.string.DeviceTransferSetup__retry),
            onButtonClick = { onEvent(DeviceTransferSetupScreenEvents.RetryClicked) }
          )
        }
      }
    }
  )
}

@Composable
private fun ProgressStep(statusText: String?) {
  CircularProgressIndicator(modifier = Modifier.size(48.dp))
  Spacer(modifier = Modifier.height(24.dp))
  if (statusText != null) {
    Text(
      text = statusText,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun VerifyStep(
  authenticationCode: Int,
  onVerified: () -> Unit,
  onRejected: () -> Unit
) {
  Text(
    text = stringResource(R.string.DeviceTransferSetup__verify_numbers_match),
    style = MaterialTheme.typography.headlineSmall,
    textAlign = TextAlign.Center
  )
  Spacer(modifier = Modifier.height(24.dp))
  Text(
    text = String.format(Locale.US, "%07d", authenticationCode),
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    textAlign = TextAlign.Center
  )
  Spacer(modifier = Modifier.height(40.dp))
  Buttons.LargeTonal(
    onClick = onVerified,
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 320.dp)
  ) {
    Text(stringResource(R.string.DeviceTransferSetup__numbers_match))
  }
  Spacer(modifier = Modifier.height(12.dp))
  Buttons.LargeTonal(
    onClick = onRejected,
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 320.dp)
  ) {
    Text(stringResource(R.string.DeviceTransferSetup__numbers_do_not_match))
  }
}

@Composable
private fun ErrorStep(
  message: String,
  buttonText: String,
  onButtonClick: () -> Unit
) {
  Text(
    text = message,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.error,
    textAlign = TextAlign.Center
  )
  Spacer(modifier = Modifier.height(24.dp))
  Buttons.LargeTonal(
    onClick = onButtonClick,
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 320.dp)
  ) {
    Text(buttonText)
  }
}

@Composable
private fun TroubleshootingStep(onTryAgain: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = stringResource(R.string.DeviceTransferSetup__unable_to_discover_old_device),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = stringResource(R.string.DeviceTransferSetup__troubleshooting_tips),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(32.dp))
    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.Center
    ) {
      Buttons.LargeTonal(
        onClick = onTryAgain,
        modifier = Modifier.widthIn(max = 320.dp)
      ) {
        Text(stringResource(R.string.DeviceTransferSetup__try_again))
      }
    }
  }
}

@AllDevicePreviews
@Composable
private fun DeviceTransferSetupScreenVerifyPreview() {
  Previews.Preview {
    RegistrationScaffold(
      content = {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          VerifyStep(authenticationCode = 1234567, onVerified = {}, onRejected = {})
        }
      }
    )
  }
}

@AllDevicePreviews
@Composable
private fun DeviceTransferSetupScreenProgressPreview() {
  Previews.Preview {
    RegistrationScaffold(
      content = {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          ProgressStep(statusText = "Preparing to connect")
        }
      }
    )
  }
}
