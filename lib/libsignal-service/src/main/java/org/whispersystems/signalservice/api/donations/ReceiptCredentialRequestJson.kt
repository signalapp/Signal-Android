/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

import org.signal.core.util.Base64
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequest

internal class ReceiptCredentialRequestJson(
  val receiptCredentialRequest: String
) {
  constructor(receiptCredentialRequest: ReceiptCredentialRequest) : this(Base64.encodeWithPadding(receiptCredentialRequest.serialize()))
}
