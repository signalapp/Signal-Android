/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.complete

import org.signal.registration.util.DebugLoggable
import org.signal.registration.util.DebugLoggableModel

data class DeviceTransferCompleteState(
  val oneTimeEvent: OneTimeEvent? = null
) : DebugLoggableModel() {
  sealed interface OneTimeEvent : DebugLoggable
}
