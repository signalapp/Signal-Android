/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

/**
 * Request JSON for confirming a PayPal one-time payment intent
 */
internal class PayPalConfirmOneTimePaymentIntentPayload(
  val amount: String,
  val currency: String,
  val level: Long,
  val payerId: String,
  val paymentId: String,
  val paymentToken: String
)
