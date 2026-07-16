/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

interface MediaSendQrRepository {
  suspend fun checkQrData(qrData: String): QrCheckResult

  sealed interface QrCheckResult {
    data object None : QrCheckResult
    data class Username(val recipientId: MediaRecipientId, val username: String) : QrCheckResult
    data object LinkDevice : QrCheckResult
    data class ReRegistration(val qrData: String) : QrCheckResult
  }
}
