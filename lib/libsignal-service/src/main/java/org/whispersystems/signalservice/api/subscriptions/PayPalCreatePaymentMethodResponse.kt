/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.subscriptions

/**
 * Response object from creating a payment method via PayPal
 */
data class PayPalCreatePaymentMethodResponse(
  val approvalUrl: String,
  val token: String
)
