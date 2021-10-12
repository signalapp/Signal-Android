package org.thoughtcrime.securesms.components.settings.app.subscription.subscribe

import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.models.CurrencySelection
import org.thoughtcrime.securesms.subscription.Subscription

data class SubscribeState(
  val previewBadge: Badge? = null,
  val currencySelection: CurrencySelection = CurrencySelection("USD"),
  val subscriptions: List<Subscription> = listOf(),
  val selectedSubscription: Subscription? = null,
  val activeSubscription: Subscription? = null,
  val isGooglePayAvailable: Boolean = false,
  val stage: Stage = Stage.INIT
) {
  enum class Stage {
    INIT,
    READY,
    PAYMENT_PIPELINE,
    CANCELLING
  }
}
