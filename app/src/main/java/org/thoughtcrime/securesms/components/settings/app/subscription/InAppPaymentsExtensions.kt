/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import org.signal.donations.CreditCardPaymentSource
import org.signal.donations.GooglePayPaymentSource
import org.signal.donations.IDEALPaymentSource
import org.signal.donations.PayPalPaymentSource
import org.signal.donations.PaymentSource
import org.signal.donations.PaymentSourceType
import org.signal.donations.SEPADebitPaymentSource
import org.signal.donations.StripeApi
import org.signal.donations.TokenPaymentSource
import org.thoughtcrime.securesms.jobs.protos.InAppPaymentSourceData

fun PaymentSourceType.toInAppPaymentSourceDataCode(): InAppPaymentSourceData.Code {
  return when (this) {
    PaymentSourceType.Unknown -> InAppPaymentSourceData.Code.UNKNOWN
    PaymentSourceType.GooglePlayBilling -> InAppPaymentSourceData.Code.GOOGLE_PLAY_BILLING
    PaymentSourceType.PayPal -> InAppPaymentSourceData.Code.PAY_PAL
    PaymentSourceType.Stripe.CreditCard -> InAppPaymentSourceData.Code.CREDIT_CARD
    PaymentSourceType.Stripe.GooglePay -> InAppPaymentSourceData.Code.GOOGLE_PAY
    PaymentSourceType.Stripe.IDEAL -> InAppPaymentSourceData.Code.IDEAL
    PaymentSourceType.Stripe.SEPADebit -> InAppPaymentSourceData.Code.SEPA_DEBIT
  }
}

fun PaymentSource.toProto(): InAppPaymentSourceData {
  return InAppPaymentSourceData(
    code = type.toInAppPaymentSourceDataCode(),
    idealData = if (this is IDEALPaymentSource) {
      InAppPaymentSourceData.IDEALData(
        name = idealData.name,
        email = idealData.email
      )
    } else null,
    sepaData = if (this is SEPADebitPaymentSource) {
      InAppPaymentSourceData.SEPAData(
        iban = sepaDebitData.iban,
        name = sepaDebitData.name,
        email = sepaDebitData.email
      )
    } else null,
    tokenData = if (this is CreditCardPaymentSource || this is GooglePayPaymentSource) {
      InAppPaymentSourceData.TokenData(
        parameters = parameterize().toString(),
        tokenId = getTokenId(),
        email = email()
      )
    } else null
  )
}

fun InAppPaymentSourceData.toPaymentSource(): PaymentSource {
  return when (code) {
    InAppPaymentSourceData.Code.CREDIT_CARD, InAppPaymentSourceData.Code.GOOGLE_PAY -> {
      TokenPaymentSource(
        type = if (code == InAppPaymentSourceData.Code.CREDIT_CARD) PaymentSourceType.Stripe.CreditCard else PaymentSourceType.Stripe.GooglePay,
        parameters = tokenData!!.parameters,
        token = tokenData.tokenId,
        email = tokenData.email
      )
    }
    InAppPaymentSourceData.Code.SEPA_DEBIT -> {
      SEPADebitPaymentSource(
        StripeApi.SEPADebitData(
          iban = sepaData!!.iban,
          name = sepaData.name,
          email = sepaData.email
        )
      )
    }
    InAppPaymentSourceData.Code.IDEAL -> {
      IDEALPaymentSource(
        StripeApi.IDEALData(
          name = idealData!!.name,
          email = idealData.email
        )
      )
    }
    InAppPaymentSourceData.Code.PAY_PAL -> {
      PayPalPaymentSource()
    }
    else -> error("Unexpected code $code")
  }
}
