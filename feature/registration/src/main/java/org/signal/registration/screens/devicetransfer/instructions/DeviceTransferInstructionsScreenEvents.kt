/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.instructions

import org.signal.registration.util.DebugLoggableModel

sealed class DeviceTransferInstructionsScreenEvents : DebugLoggableModel() {
  data object ContinueClicked : DeviceTransferInstructionsScreenEvents()
  data object BackClicked : DeviceTransferInstructionsScreenEvents()
  data object ConsumeOneTimeEvent : DeviceTransferInstructionsScreenEvents()
}
