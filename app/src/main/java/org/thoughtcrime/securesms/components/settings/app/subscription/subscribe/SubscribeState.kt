package org.thoughtcrime.securesms.components.settings.app.subscription.subscribe

import org.thoughtcrime.securesms.components.settings.app.subscription.models.CurrencySelection
import org.thoughtcrime.securesms.subscription.Subscription
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription

data class SubscribeState(
  val currencySelection: CurrencySelection = CurrencySelection("USD"),
  val subscriptions: List<Subscription> = listOf(),
  val selectedSubscription: Subscription? = null,
  val activeSubscription: ActiveSubscription? = null,
  val isGooglePayAvailable: Boolean = false,
  val stage: Stage = Stage.INIT,
  val hasInProgressSubscriptionTransaction: Boolean = false,
) {
  enum class Stage {
    INIT,
    READY,
    TOKEN_REQUEST,
    PAYMENT_PIPELINE,
    CANCELLING
  }
}
