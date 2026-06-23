/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.setup

import org.signal.registration.util.DebugLoggable
import org.signal.registration.util.DebugLoggableModel

data class DeviceTransferSetupState(
  val step: SetupStep = SetupStep.INITIAL,
  val authenticationCode: Int? = null,
  val takingTooLong: Boolean = false,
  val showVerifyRejectDialog: Boolean = false,
  val showErrorDialog: Boolean = false,
  val oneTimeEvent: OneTimeEvent? = null
) : DebugLoggableModel() {

  sealed interface OneTimeEvent : DebugLoggable {
    /** The screen should launch a runtime permission request. */
    data object RequestLocationPermission : OneTimeEvent

    /** The screen should launch the system Location settings. */
    data object OpenLocationSettings : OneTimeEvent

    /** The screen should launch the system Wi-Fi settings. */
    data object OpenWifiSettings : OneTimeEvent

    /** The screen should launch this app's system settings (for permanent-denial recovery). */
    data object OpenAppSettings : OneTimeEvent

    /** Both devices verified successfully; navigate to the Progress screen. */
    data object NavigateToProgress : OneTimeEvent

    /** Unrecoverable setup path (e.g. Wi-Fi Direct unavailable); navigate back. */
    data object NavigateAway : OneTimeEvent
  }
}
