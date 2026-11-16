/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import org.signal.core.util.Base64
import org.signal.core.util.censor
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.receipts.ReceiptCredential
import java.io.IOException

/**
 * A manually-pasted, base64-encoded receipt credential. Wrapping it keeps the raw text from being logged by accident:
 * [toString] is censored, so anything that prints this (or a class that holds it) is safe by default.
 */
@JvmInline
value class ManualReceiptCredential(val value: String) {

  val isBlank: Boolean
    get() = value.isBlank()

  val isNotBlank: Boolean
    get() = value.isNotBlank()

  /** Parses the pasted text as a receipt credential, or null if it isn't a valid one. */
  fun decodeOrNull(): ReceiptCredential? {
    return try {
      ReceiptCredential(Base64.decode(value.trim()))
    } catch (e: IOException) {
      null
    } catch (e: InvalidInputException) {
      null
    }
  }

  override fun toString(): String = "ManualReceiptCredential(${value.censor()})"

  companion object {
    val EMPTY = ManualReceiptCredential("")
  }
}
