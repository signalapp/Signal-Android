package org.thoughtcrime.securesms.components.settings.app.subscription.subscribe

import org.thoughtcrime.securesms.subscription.Subscription
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.util.Currency

data class SubscribeState(
  val currencySelection: Currency,
  val subscriptions: List<Subscription> = listOf(),
  val selectedSubscription: Subscription? = null,
  val activeSubscription: ActiveSubscription? = null,
  val isGooglePayAvailable: Boolean = false,
  val stage: Stage = Stage.INIT,
  val hasInProgressSubscriptionTransaction: Boolean = false,
) {

  fun isSubscriptionExpiring(): Boolean {
    return activeSubscription?.isActive == true && activeSubscription.activeSubscription.willCancelAtPeriodEnd()
  }

  enum class Stage {
    INIT,
    READY,
    TOKEN_REQUEST,
    PAYMENT_PIPELINE,
    CANCELLING,
    FAILURE
  }
}
