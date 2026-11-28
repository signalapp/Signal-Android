/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.devicetransfer.app

import org.signal.devicetransfer.TransferStatus.TransferMode

sealed interface DeviceTransferTestEvent {

  data object ScreenResumed : DeviceTransferTestEvent

  data object PermissionsResultReceived : DeviceTransferTestEvent

  data object GrantPermissionsClicked : DeviceTransferTestEvent

  data class PayloadSizeSelected(val size: DeviceTransferTestState.PayloadSize) : DeviceTransferTestEvent

  data object StartServerClicked : DeviceTransferTestEvent

  data object StartClientClicked : DeviceTransferTestEvent

  data object RestartClicked : DeviceTransferTestEvent

  data object StopClicked : DeviceTransferTestEvent

  data object ClearLogClicked : DeviceTransferTestEvent

  data class VerificationAnswered(val codesMatch: Boolean) : DeviceTransferTestEvent

  /** The transfer service posted a new status. [authenticationCode] is only set for verification. */
  data class TransferStatusReceived(val mode: TransferMode, val authenticationCode: Int) : DeviceTransferTestEvent

  data class TaskUpdateReceived(val update: TestTaskUpdate) : DeviceTransferTestEvent
}
