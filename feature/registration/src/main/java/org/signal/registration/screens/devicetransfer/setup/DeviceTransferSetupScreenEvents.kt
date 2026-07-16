/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.setup

sealed class DeviceTransferSetupScreenEvents {
  /** Kick off the full check-and-start sequence. Emitted once on initial composition and on retries. */
  data object CheckPermissions : DeviceTransferSetupScreenEvents()

  /** Emitted by the screen after the accompanist permission request resolves. */
  data object PermissionsGranted : DeviceTransferSetupScreenEvents()
  data object PermissionsDenied : DeviceTransferSetupScreenEvents()

  /** Emitted when the user taps "Grant location permission" on the error surface. */
  data object RequestPermissionClicked : DeviceTransferSetupScreenEvents()

  /** Emitted when the user taps the error-surface recovery button for location/wifi. */
  data object OpenLocationSettingsClicked : DeviceTransferSetupScreenEvents()
  data object OpenWifiSettingsClicked : DeviceTransferSetupScreenEvents()
  data object OpenAppSettingsClicked : DeviceTransferSetupScreenEvents()

  /** Re-check the current gate after returning from system settings. */
  data object OnResume : DeviceTransferSetupScreenEvents()

  /** User confirmed the SAS numbers match. */
  data object UserVerifiedCode : DeviceTransferSetupScreenEvents()

  /** User tapped "numbers do not match". Shows a confirmation dialog. */
  data object UserRejectedCode : DeviceTransferSetupScreenEvents()
  data object VerifyRejectConfirmed : DeviceTransferSetupScreenEvents()
  data object VerifyRejectDismissed : DeviceTransferSetupScreenEvents()

  /** Retry button from the error / troubleshooting screens. */
  data object RetryClicked : DeviceTransferSetupScreenEvents()

  /** Back / close. Stops the service and pops the nav stack. */
  data object BackClicked : DeviceTransferSetupScreenEvents()

  /** The screen has launched the pending location permission request. */
  data object RequestLocationPermissionHandled : DeviceTransferSetupScreenEvents()

  /** The screen has launched the pending open-location-settings action. */
  data object OpenLocationSettingsHandled : DeviceTransferSetupScreenEvents()

  /** The screen has launched the pending open-wifi-settings action. */
  data object OpenWifiSettingsHandled : DeviceTransferSetupScreenEvents()

  /** The screen has launched the pending open-app-settings action. */
  data object OpenAppSettingsHandled : DeviceTransferSetupScreenEvents()
}
