/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.util.Base64
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialResponse

internal class ReceiptCredentialResponseJson(
  @JsonProperty("receiptCredentialResponse") receiptCredentialResponse: String
) {
  /** Null if the server sent something that isn't a serialized [ReceiptCredentialResponse]. */
  val credentialResponse: ReceiptCredentialResponse? = runCatching { ReceiptCredentialResponse(Base64.decode(receiptCredentialResponse)) }.getOrNull()
}
