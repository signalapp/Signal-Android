/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.google.android.gms.common.ConnectionResult
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R

/**
 * Represents the availability status of Google Play Services on the device.
 *
 * Maps Google Play Services ConnectionResult codes to enum values for easier handling
 * in the application. Each enum value corresponds to a specific state that determines
 * what dialog or action should be presented to the user.
 *
 * @param code The corresponding ConnectionResult code from Google Play Services
 */
enum class GooglePlayServicesAvailability(val code: Int) {
  /** An unknown code. Possibly due to an update on Google's end */
  UNKNOWN(code = Int.MIN_VALUE),

  /** Google Play Services is available and ready to use */
  SUCCESS(code = ConnectionResult.SUCCESS),

  /** Google Play Services is not installed on the device */
  SERVICE_MISSING(code = ConnectionResult.SERVICE_MISSING),

  /** Google Play Services is currently being updated */
  SERVICE_UPDATING(code = ConnectionResult.SERVICE_UPDATING),

  /** Google Play Services requires an update to a newer version */
  SERVICE_VERSION_UPDATE_REQUIRED(code = ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED),

  /** Google Play Services is installed but disabled by the user */
  SERVICE_DISABLED(code = ConnectionResult.SERVICE_DISABLED),

  /** Google Play Services installation is invalid or corrupted */
  SERVICE_INVALID(code = ConnectionResult.SERVICE_INVALID);

  companion object {

    private val TAG = Log.tag(GooglePlayServicesAvailability::class)

    /**
     * Converts a Google Play Services ConnectionResult code to the corresponding enum value.
     *
     * @param code The ConnectionResult code from Google Play Services
     * @return The matching GooglePlayServicesAvailability enum value
     */
    fun fromCode(code: Int): GooglePlayServicesAvailability {
      val availability = entries.firstOrNull { it.code == code } ?: UNKNOWN
      if (availability == UNKNOWN) {
        Log.w(TAG, "Unknown availability code: $code")
      }

      return availability
    }
  }
}

/**
 * Displays a dialog based on the Google Play Services availability status.
 *
 * Shows different dialogs with appropriate messages and actions depending on whether
 * Google Play Services is missing, updating, requires an update, is disabled, or invalid.
 * When availability is SUCCESS, automatically calls onDismissRequest to dismiss any dialog.
 *
 * @param onDismissRequest Callback invoked when the dialog is dismissed or when SUCCESS status is received
 * @param onLearnMoreClick Callback invoked when the "Learn More" action is selected
 * @param onMakeServicesAvailableClick Callback invoked when an action to make services
 *   available is selected (e.g., install or update)
 * @param googlePlayServicesAvailability The current availability status of Google Play Services
 */
@Composable
fun GooglePlayServicesAvailabilityDialog(
  onDismissRequest: () -> Unit,
  onLearnMoreClick: () -> Unit,
  onMakeServicesAvailableClick: () -> Unit,
  googlePlayServicesAvailability: GooglePlayServicesAvailability
) {
  when (googlePlayServicesAvailability) {
    GooglePlayServicesAvailability.SUCCESS -> {
      LaunchedEffect(Unit) {
        onDismissRequest()
      }
    }
    GooglePlayServicesAvailability.SERVICE_MISSING, GooglePlayServicesAvailability.UNKNOWN -> {
      ServiceMissingDialog(
        onDismissRequest = onDismissRequest,
        onInstallPlayServicesClick = onMakeServicesAvailableClick
      )
    }
    GooglePlayServicesAvailability.SERVICE_UPDATING -> {
      ServiceUpdatingDialog(onDismissRequest = onDismissRequest)
    }
    GooglePlayServicesAvailability.SERVICE_VERSION_UPDATE_REQUIRED -> {
      ServiceVersionUpdateRequiredDialog(
        onDismissRequest = onDismissRequest,
        onUpdateClick = onMakeServicesAvailableClick
      )
    }
    GooglePlayServicesAvailability.SERVICE_DISABLED -> {
      ServiceDisabledDialog(
        onDismissRequest = onDismissRequest,
        onLearnMoreClick = onLearnMoreClick
      )
    }
    GooglePlayServicesAvailability.SERVICE_INVALID -> {
      ServiceInvalidDialog(
        onDismissRequest = onDismissRequest,
        onLearnMoreClick = onLearnMoreClick
      )
    }
  }
}

@Composable
private fun ServiceMissingDialog(onDismissRequest: () -> Unit, onInstallPlayServicesClick: () -> Unit) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.GooglePlayServicesAvailability__service_missing_title),
    body = stringResource(R.string.GooglePlayServicesAvailability__service_missing_message),
    confirm = stringResource(R.string.GooglePlayServicesAvailability__install_play_services),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = {},
    onDeny = onInstallPlayServicesClick,
    onDismiss = onDismissRequest,
    onDismissRequest = onDismissRequest
  )
}

@Composable
private fun ServiceUpdatingDialog(onDismissRequest: () -> Unit) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.GooglePlayServicesAvailability__service_updating_title),
    body = stringResource(R.string.GooglePlayServicesAvailability__service_updating_message),
    confirm = stringResource(android.R.string.ok),
    onConfirm = {},
    onDismiss = onDismissRequest,
    onDismissRequest = onDismissRequest
  )
}

@Composable
private fun ServiceVersionUpdateRequiredDialog(onDismissRequest: () -> Unit, onUpdateClick: () -> Unit) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.GooglePlayServicesAvailability__service_update_required_title),
    body = stringResource(R.string.GooglePlayServicesAvailability__service_update_required_message),
    confirm = stringResource(R.string.GooglePlayServicesAvailability__update),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = onUpdateClick,
    onDismiss = onDismissRequest,
    onDismissRequest = onDismissRequest
  )
}

@Composable
private fun ServiceDisabledDialog(onDismissRequest: () -> Unit, onLearnMoreClick: () -> Unit) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.GooglePlayServicesAvailability__service_disabled_title),
    body = stringResource(R.string.GooglePlayServicesAvailability__service_disabled_message),
    confirm = stringResource(android.R.string.ok),
    dismiss = stringResource(R.string.GooglePlayServicesAvailability__learn_more),
    onConfirm = onDismissRequest,
    onDeny = onLearnMoreClick,
    onDismiss = onDismissRequest,
    onDismissRequest = onDismissRequest
  )
}

@Composable
private fun ServiceInvalidDialog(onDismissRequest: () -> Unit, onLearnMoreClick: () -> Unit) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.GooglePlayServicesAvailability__service_disabled_title),
    body = stringResource(R.string.GooglePlayServicesAvailability__service_invalid_message),
    confirm = stringResource(android.R.string.ok),
    dismiss = stringResource(R.string.GooglePlayServicesAvailability__learn_more),
    onConfirm = {},
    onDeny = onLearnMoreClick,
    onDismiss = onDismissRequest,
    onDismissRequest = onDismissRequest
  )
}

@DayNightPreviews
@Composable
private fun ServiceMissingDialogPreview() {
  Previews.Preview {
    ServiceMissingDialog({}, {})
  }
}

@DayNightPreviews
@Composable
private fun ServiceUpdatingDialogPreview() {
  Previews.Preview {
    ServiceUpdatingDialog({})
  }
}

@DayNightPreviews
@Composable
private fun ServiceVersionUpdateRequiredDialogPreview() {
  Previews.Preview {
    ServiceVersionUpdateRequiredDialog({}, {})
  }
}

@DayNightPreviews
@Composable
private fun ServiceDisabledDialogPreview() {
  Previews.Preview {
    ServiceDisabledDialog({}, {})
  }
}

@DayNightPreviews
@Composable
private fun ServiceInvalidDialogPreview() {
  Previews.Preview {
    ServiceInvalidDialog({}, {})
  }
}
