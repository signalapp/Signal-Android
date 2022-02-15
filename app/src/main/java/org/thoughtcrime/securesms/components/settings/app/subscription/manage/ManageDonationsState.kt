package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.subscription.Subscription
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription

data class ManageDonationsState(
  val featuredBadge: Badge? = null,
  val transactionState: TransactionState = TransactionState.Init,
  val availableSubscriptions: List<Subscription> = emptyList(),
  private val subscriptionRedemptionState: SubscriptionRedemptionState = SubscriptionRedemptionState.NONE
) {

  fun getRedemptionState(): SubscriptionRedemptionState {
    return when (transactionState) {
      TransactionState.Init -> subscriptionRedemptionState
      TransactionState.InTransaction -> SubscriptionRedemptionState.IN_PROGRESS
      is TransactionState.NotInTransaction -> getStateFromActiveSubscription(transactionState.activeSubscription) ?: subscriptionRedemptionState
    }
  }

  fun getStateFromActiveSubscription(activeSubscription: ActiveSubscription): SubscriptionRedemptionState? {
    return when {
      activeSubscription.isFailedPayment -> SubscriptionRedemptionState.FAILED
      activeSubscription.isInProgress -> SubscriptionRedemptionState.IN_PROGRESS
      else -> null
    }
  }

  sealed class TransactionState {
    object Init : TransactionState()
    object InTransaction : TransactionState()
    class NotInTransaction(val activeSubscription: ActiveSubscription) : TransactionState()
  }

  enum class SubscriptionRedemptionState {
    NONE,
    IN_PROGRESS,
    FAILED
  }
}
