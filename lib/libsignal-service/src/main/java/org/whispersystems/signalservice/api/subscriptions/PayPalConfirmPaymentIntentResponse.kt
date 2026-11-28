/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.subscriptions

/**
 * Response object from confirming a payment intent via PayPal
 */
data class PayPalConfirmPaymentIntentResponse(
  val paymentId: String
)
