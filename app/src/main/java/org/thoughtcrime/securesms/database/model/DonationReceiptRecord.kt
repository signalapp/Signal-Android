package org.thoughtcrime.securesms.database.model

import org.signal.core.util.money.FiatMoney
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.util.Currency

data class DonationReceiptRecord(
  val id: Long = -1L,
  val amount: FiatMoney,
  val timestamp: Long,
  val type: Type,
  val subscriptionLevel: Int
) {
  enum class Type(val code: String) {
    RECURRING("recurring"),
    BOOST("boost");

    companion object {
      fun fromCode(code: String): Type {
        return values().first { it.code == code }
      }
    }
  }

  companion object {
    @JvmStatic
    fun createForSubscription(subscription: ActiveSubscription.Subscription): DonationReceiptRecord {
      val activeCurrency = Currency.getInstance(subscription.currency)
      val activeAmount = subscription.amount.movePointLeft(activeCurrency.defaultFractionDigits)

      return DonationReceiptRecord(
        id = -1L,
        amount = FiatMoney(activeAmount, activeCurrency),
        timestamp = System.currentTimeMillis(),
        subscriptionLevel = subscription.level,
        type = Type.RECURRING
      )
    }

    fun createForBoost(amount: FiatMoney): DonationReceiptRecord {
      return DonationReceiptRecord(
        id = -1L,
        amount = amount,
        timestamp = System.currentTimeMillis(),
        subscriptionLevel = -1,
        type = Type.BOOST
      )
    }
  }
}
