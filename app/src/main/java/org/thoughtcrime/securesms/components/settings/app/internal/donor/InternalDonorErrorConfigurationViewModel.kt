package org.thoughtcrime.securesms.components.settings.app.internal.donor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.donations.StripeDeclineCode
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.UnexpectedSubscriptionCancellation
import org.thoughtcrime.securesms.components.settings.app.subscription.getBoostBadges
import org.thoughtcrime.securesms.components.settings.app.subscription.getGiftBadges
import org.thoughtcrime.securesms.components.settings.app.subscription.getSubscriptionLevels
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.util.Locale
import kotlin.concurrent.withLock

class InternalDonorErrorConfigurationViewModel : ViewModel() {

  private val store = MutableStateFlow(InternalDonorErrorConfigurationState())

  val state: StateFlow<InternalDonorErrorConfigurationState> = store

  init {
    viewModelScope.launch(SignalDispatchers.IO) {
      val configuration = AppDependencies.donationsService.getDonationsConfiguration(Locale.getDefault()).toNetworkResult().successOrNull() ?: return@launch
      val giftBadges = configuration.getGiftBadges()
      val boostBadges = configuration.getBoostBadges()
      val subscriptionBadges = configuration.getSubscriptionLevels().values.map { Badges.fromServiceBadge(it.badge) }

      store.update { it.copy(badges = giftBadges + boostBadges + subscriptionBadges) }
    }
  }

  fun setSelectedBadge(badgeIndex: Int) {
    store.update {
      it.copy(selectedBadge = if (badgeIndex in it.badges.indices) it.badges[badgeIndex] else null)
    }
  }

  fun setSelectedUnexpectedSubscriptionCancellation(unexpectedSubscriptionCancellationIndex: Int) {
    store.update {
      it.copy(
        selectedUnexpectedSubscriptionCancellation = if (unexpectedSubscriptionCancellationIndex in UnexpectedSubscriptionCancellation.entries.toTypedArray().indices) {
          UnexpectedSubscriptionCancellation.entries[unexpectedSubscriptionCancellationIndex]
        } else {
          null
        }
      )
    }
  }

  fun setStripeDeclineCode(stripeDeclineCodeIndex: Int) {
    store.update {
      it.copy(
        selectedStripeDeclineCode = if (stripeDeclineCodeIndex in StripeDeclineCode.Code.entries.toTypedArray().indices) {
          StripeDeclineCode.Code.entries[stripeDeclineCodeIndex]
        } else {
          null
        }
      )
    }
  }

  fun save(): Completable {
    val snapshot = store.value
    val saveState = Completable.fromAction {
      InAppPaymentSubscriberRecord.Type.DONATION.lock.withLock {
        when {
          snapshot.selectedBadge?.isGift() == true -> handleGiftExpiration(snapshot)
          snapshot.selectedBadge?.isBoost() == true -> handleBoostExpiration(snapshot)
          snapshot.selectedBadge?.isSubscription() == true -> handleSubscriptionExpiration(snapshot)
          else -> handleSubscriptionPaymentFailure(snapshot)
        }
      }
    }.subscribeOn(Schedulers.io())

    return clearErrorState().andThen(saveState)
  }

  fun clearErrorState(): Completable {
    return Completable.fromAction {
      InAppPaymentSubscriberRecord.Type.DONATION.lock.withLock {
        SignalStore.inAppPayments.setExpiredBadge(null)
        SignalStore.inAppPayments.setExpiredGiftBadge(null)
        SignalStore.inAppPayments.unexpectedSubscriptionCancelationReason = null
        SignalStore.inAppPayments.unexpectedSubscriptionCancelationTimestamp = 0L
        SignalStore.inAppPayments.setUnexpectedSubscriptionCancelationChargeFailure(null)
      }

      store.update {
        it.copy(
          selectedStripeDeclineCode = null,
          selectedUnexpectedSubscriptionCancellation = null,
          selectedBadge = null
        )
      }
    }
  }

  private fun handleBoostExpiration(state: InternalDonorErrorConfigurationState) {
    SignalStore.inAppPayments.setExpiredBadge(state.selectedBadge)
  }

  private fun handleGiftExpiration(state: InternalDonorErrorConfigurationState) {
    SignalStore.inAppPayments.setExpiredGiftBadge(state.selectedBadge)
  }

  private fun handleSubscriptionExpiration(state: InternalDonorErrorConfigurationState) {
    SignalStore.inAppPayments.updateLocalStateForLocalSubscribe(InAppPaymentSubscriberRecord.Type.DONATION)
    SignalStore.inAppPayments.setExpiredBadge(state.selectedBadge)
    handleSubscriptionPaymentFailure(state)
  }

  private fun handleSubscriptionPaymentFailure(state: InternalDonorErrorConfigurationState) {
    SignalStore.inAppPayments.unexpectedSubscriptionCancelationReason = state.selectedUnexpectedSubscriptionCancellation?.status
    SignalStore.inAppPayments.unexpectedSubscriptionCancelationTimestamp = System.currentTimeMillis()
    SignalStore.inAppPayments.showMonthlyDonationCanceledDialog = true
    SignalStore.inAppPayments.setUnexpectedSubscriptionCancelationChargeFailure(
      state.selectedStripeDeclineCode?.let {
        ActiveSubscription.ChargeFailure(
          it.code,
          "Test Charge Failure",
          "Test Network Status",
          it.code,
          "Test"
        )
      }
    )
  }
}
