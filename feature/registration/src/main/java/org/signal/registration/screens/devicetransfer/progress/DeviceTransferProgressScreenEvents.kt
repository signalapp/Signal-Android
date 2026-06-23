/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.progress

import org.signal.registration.util.DebugLoggableModel

sealed class DeviceTransferProgressScreenEvents : DebugLoggableModel() {
  data object CancelClicked : DeviceTransferProgressScreenEvents()
  data object CancelConfirmed : DeviceTransferProgressScreenEvents()
  data object CancelDismissed : DeviceTransferProgressScreenEvents()
  data object TryAgainClicked : DeviceTransferProgressScreenEvents()
  data object ConsumeOneTimeEvent : DeviceTransferProgressScreenEvents()
}
