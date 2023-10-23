/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import okio.ByteString
import org.signal.core.util.money.FiatMoney
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.model.databaseprotos.DecimalValue
import org.thoughtcrime.securesms.database.model.databaseprotos.FiatValue
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.util.Currency
import kotlin.time.Duration.Companion.days

object DonationSerializationHelper {

  private val PENDING_ONE_TIME_BANK_TRANSFER_TIMEOUT = 14.days
  private val PENDING_ONE_TIME_NORMAL_TIMEOUT = 1.days

  val PendingOneTimeDonation.isExpired: Boolean
    get() {
      val timeout = if (paymentMethodType == PendingOneTimeDonation.PaymentMethodType.SEPA_DEBIT) {
        PENDING_ONE_TIME_BANK_TRANSFER_TIMEOUT
      } else {
        PENDING_ONE_TIME_NORMAL_TIMEOUT
      }

      return (timestamp + timeout.inWholeMilliseconds) < System.currentTimeMillis()
    }

  fun createPendingOneTimeDonationProto(
    badge: Badge,
    paymentSourceType: PaymentSourceType,
    amount: FiatMoney
  ): PendingOneTimeDonation {
    return PendingOneTimeDonation(
      badge = Badges.toDatabaseBadge(badge),
      paymentMethodType = when (paymentSourceType) {
        PaymentSourceType.PayPal -> PendingOneTimeDonation.PaymentMethodType.PAYPAL
        PaymentSourceType.Stripe.CreditCard, PaymentSourceType.Stripe.GooglePay, PaymentSourceType.Unknown -> PendingOneTimeDonation.PaymentMethodType.CARD
        PaymentSourceType.Stripe.SEPADebit -> PendingOneTimeDonation.PaymentMethodType.SEPA_DEBIT
        PaymentSourceType.Stripe.IDEAL -> PendingOneTimeDonation.PaymentMethodType.IDEAL
      },
      amount = amount.toFiatValue(),
      timestamp = System.currentTimeMillis()
    )
  }

  fun FiatValue.toFiatMoney(): FiatMoney {
    return FiatMoney(
      amount!!.toBigDecimal(),
      Currency.getInstance(currencyCode)
    )
  }

  fun DecimalValue.toBigDecimal(): BigDecimal {
    return BigDecimal(
      BigInteger(value_.toByteArray()),
      scale,
      MathContext(precision)
    )
  }

  fun FiatMoney.toFiatValue(): FiatValue {
    return FiatValue(
      currencyCode = currency.currencyCode,
      amount = amount.toDecimalValue()
    )
  }

  fun BigDecimal.toDecimalValue(): DecimalValue {
    return DecimalValue(
      scale = scale(),
      precision = precision(),
      value_ = ByteString.of(*this.unscaledValue().toByteArray())
    )
  }
}
