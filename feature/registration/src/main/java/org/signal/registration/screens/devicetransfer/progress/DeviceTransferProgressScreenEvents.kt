/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.progress

sealed class DeviceTransferProgressScreenEvents {
  data object CancelClicked : DeviceTransferProgressScreenEvents()
  data object CancelConfirmed : DeviceTransferProgressScreenEvents()
  data object CancelDismissed : DeviceTransferProgressScreenEvents()
  data object TryAgainClicked : DeviceTransferProgressScreenEvents()
}
