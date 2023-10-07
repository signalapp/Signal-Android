/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.donations

import org.json.JSONObject

class SEPADebitPaymentSource(
  val sepaDebitData: StripeApi.SEPADebitData
) : StripeApi.PaymentSource {
  override val type: PaymentSourceType = PaymentSourceType.Stripe.SEPADebit

  override fun parameterize(): JSONObject = error("SEPA Debit does not support tokenization")

  override fun getTokenId(): String = error("SEPA Debit does not support tokenization")
  override fun email(): String? = null
}
