/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.complete

import org.signal.registration.util.DebugLoggableModel

sealed class DeviceTransferCompleteScreenEvents : DebugLoggableModel() {
  data object ContinueClicked : DeviceTransferCompleteScreenEvents()
  data object ConsumeOneTimeEvent : DeviceTransferCompleteScreenEvents()
}
