/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing.endpoints

import org.signal.core.util.Base64
import org.signal.libsignal.zkgroup.ServerSecretParams
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequest
import org.signal.libsignal.zkgroup.receipts.ServerZkReceiptOperations

/**
 * Test-side stand-in for the server's donation zk operations.
 *
 * Generates its own [ServerSecretParams] so it can issue receipt credentials that validate against
 * [clientReceiptOperations]. The instrumentation dependency provider installs
 * [clientReceiptOperations] in place of the production `ClientZkReceiptOperations`, so an integration
 * test mints genuinely valid credentials and the real client-side validation in the receipt-credential
 * context job runs for real — only the issuing params are swapped for test ones.
 */
object DonationTestServer {

  private const val SECONDS_PER_DAY = 86_400L

  private val serverSecretParams: ServerSecretParams = ServerSecretParams.generate()
  private val serverReceiptOperations = ServerZkReceiptOperations(serverSecretParams)

  /** Installed by the instrumentation provider so client validation matches credentials issued here. */
  val clientReceiptOperations: ClientZkReceiptOperations = ClientZkReceiptOperations(serverSecretParams.publicParams)

  /**
   * Issues a valid receipt credential for the client's serialized [requestBase64], returning the
   * base64 body the client expects in a `receiptCredentialResponse` field. The expiration is aligned
   * to a day boundary as libsignal requires.
   */
  fun issueReceiptCredential(
    requestBase64: String,
    receiptLevel: Long = 1L,
    receiptExpiration: Long = dayAlignedExpiration()
  ): String {
    val request = ReceiptCredentialRequest(Base64.decode(requestBase64))
    val response = serverReceiptOperations.issueReceiptCredential(request, receiptExpiration, receiptLevel)
    return Base64.encodeWithPadding(response.serialize())
  }

  private fun dayAlignedExpiration(): Long {
    val nowSeconds = System.currentTimeMillis() / 1000
    val ninetyDays = 90 * SECONDS_PER_DAY
    return ((nowSeconds + ninetyDays) / SECONDS_PER_DAY) * SECONDS_PER_DAY
  }
}
