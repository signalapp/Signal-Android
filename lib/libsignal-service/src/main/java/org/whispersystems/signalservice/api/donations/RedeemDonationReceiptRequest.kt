/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

/**
 * POST /v1/donation/redeem-receipt
 *
 * Request object for redeeming a receipt from a donation transaction.
 *
 * @param receiptCredentialPresentation base64-encoded no-newlines standard-character-set with-padding of the bytes of a `ReceiptCredentialPresentation`
 * @param visible Whether the new badge should be visible on the profile
 * @param primary Whether the new badge should be primary on the profile; always treated as false if [visible] is false
 */
internal class RedeemDonationReceiptRequest(
  val receiptCredentialPresentation: String,
  val visible: Boolean,
  val primary: Boolean
)
