/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.donations

class SEPADebitPaymentSource(
  val sepaDebitData: StripeApi.SEPADebitData
) : PaymentSource {
  override val type: PaymentSourceType = PaymentSourceType.Stripe.SEPADebit

  override fun email(): String? = null
}
