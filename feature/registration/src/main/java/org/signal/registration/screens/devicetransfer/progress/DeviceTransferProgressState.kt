/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.progress

import org.signal.registration.util.DebugLoggable
import org.signal.registration.util.DebugLoggableModel

data class DeviceTransferProgressState(
  val messageCount: Long = 0,
  val status: Status = Status.RECEIVING,
  val errorReason: ErrorReason? = null,
  val oneTimeEvent: OneTimeEvent? = null
) : DebugLoggableModel() {

  enum class Status {
    RECEIVING,
    IMPORTING,
    FINALIZING,
    FAILED
  }

  enum class ErrorReason {
    VERSION_DOWNGRADE,
    FOREIGN_KEY,
    UNKNOWN
  }

  sealed interface OneTimeEvent : DebugLoggable {
    data object TransferCanceled : OneTimeEvent
  }
}
