/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

/**
 * Outcome of the zkgroup operations in [NetworkController] that have to verify a receipt credential before they can
 * produce anything.
 */
sealed interface ReceiptCredentialResult<out T> {
  data class Success<out T>(val value: T) : ReceiptCredentialResult<T>

  /** The credential does not match the request it answers, or was not issued by this environment's service. */
  data object VerificationFailed : ReceiptCredentialResult<Nothing>
}
