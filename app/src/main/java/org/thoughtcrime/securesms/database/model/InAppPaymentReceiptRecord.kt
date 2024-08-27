package org.thoughtcrime.securesms.database.model

import org.signal.core.util.money.FiatMoney
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.util.Currency

data class InAppPaymentReceiptRecord(
  val id: Long = -1L,
  val amount: FiatMoney,
  val timestamp: Long,
  val type: Type,
  val subscriptionLevel: Int
) {
  enum class Type(val code: String) {
    RECURRING_BACKUP("recurring_backup"),
    RECURRING_DONATION("recurring"),
    ONE_TIME_DONATION("boost"),
    ONE_TIME_GIFT("gift");

    companion object {
      fun fromCode(code: String): Type {
        return entries.first { it.code == code }
      }
    }
  }

  companion object {
    @JvmStatic
    fun createForSubscription(subscription: ActiveSubscription.Subscription): InAppPaymentReceiptRecord {
      val activeCurrency = Currency.getInstance(subscription.currency)
      val activeAmount = subscription.amount.movePointLeft(activeCurrency.defaultFractionDigits)

      return InAppPaymentReceiptRecord(
        id = -1L,
        amount = FiatMoney(activeAmount, activeCurrency),
        timestamp = System.currentTimeMillis(),
        subscriptionLevel = subscription.level,
        type = if (subscription.level == SubscriptionsConfiguration.BACKUPS_LEVEL) Type.RECURRING_BACKUP else Type.RECURRING_DONATION
      )
    }

    fun createForBoost(amount: FiatMoney): InAppPaymentReceiptRecord {
      return InAppPaymentReceiptRecord(
        id = -1L,
        amount = amount,
        timestamp = System.currentTimeMillis(),
        subscriptionLevel = -1,
        type = Type.ONE_TIME_DONATION
      )
    }

    fun createForGift(amount: FiatMoney): InAppPaymentReceiptRecord {
      return InAppPaymentReceiptRecord(
        id = -1L,
        amount = amount,
        timestamp = System.currentTimeMillis(),
        subscriptionLevel = -1,
        type = Type.ONE_TIME_GIFT
      )
    }
  }
}
