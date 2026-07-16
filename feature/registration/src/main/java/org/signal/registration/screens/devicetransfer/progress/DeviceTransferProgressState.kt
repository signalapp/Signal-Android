/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.progress

data class DeviceTransferProgressState(
  val messageCount: Long = 0,
  val status: Status = Status.RECEIVING,
  val errorReason: ErrorReason? = null
) {

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
}
