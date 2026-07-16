/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.setup

data class DeviceTransferSetupState(
  val step: SetupStep = SetupStep.INITIAL,
  val authenticationCode: Int? = null,
  val takingTooLong: Boolean = false,
  val showVerifyRejectDialog: Boolean = false,
  val showErrorDialog: Boolean = false,
  val pendingActions: PendingActions = PendingActions()
) {

  override fun toString(): String = "DeviceTransferSetupState(step=$step, authenticationCode=${authenticationCode?.let { "present" }}, takingTooLong=$takingTooLong, showVerifyRejectDialog=$showVerifyRejectDialog, showErrorDialog=$showErrorDialog, pendingActions=$pendingActions)"

  /** One-shot actions the screen should launch. The screen clears these once launched. */
  data class PendingActions(
    /** The screen should launch a runtime permission request. */
    val requestLocationPermission: Boolean = false,

    /** The screen should launch the system Location settings. */
    val openLocationSettings: Boolean = false,

    /** The screen should launch the system Wi-Fi settings. */
    val openWifiSettings: Boolean = false,

    /** The screen should launch this app's system settings (for permanent-denial recovery). */
    val openAppSettings: Boolean = false
  )
}
