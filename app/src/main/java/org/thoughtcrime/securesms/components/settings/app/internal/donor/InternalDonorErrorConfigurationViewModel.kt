package org.thoughtcrime.securesms.components.settings.app.internal.donor

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.donations.StripeDeclineCode
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.UnexpectedSubscriptionCancellation
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.SubscriptionReceiptRequestResponseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.util.Locale

class InternalDonorErrorConfigurationViewModel : ViewModel() {

  private val store = RxStore(InternalDonorErrorConfigurationState())
  private val disposables = CompositeDisposable()

  val state: Flowable<InternalDonorErrorConfigurationState> = store.stateFlowable

  init {
    val giftBadges: Single<List<Badge>> = Single
      .fromCallable {
        ApplicationDependencies.getDonationsService()
          .getGiftBadges(Locale.getDefault())
      }
      .flatMap { it.flattenResult() }
      .map { results -> results.values.map { Badges.fromServiceBadge(it) } }
      .subscribeOn(Schedulers.io())

    val boostBadges: Single<List<Badge>> = Single
      .fromCallable {
        ApplicationDependencies.getDonationsService()
          .getBoostBadge(Locale.getDefault())
      }
      .flatMap { it.flattenResult() }
      .map { listOf(Badges.fromServiceBadge(it)) }
      .subscribeOn(Schedulers.io())

    val subscriptionBadges: Single<List<Badge>> = Single
      .fromCallable {
        ApplicationDependencies.getDonationsService()
          .getSubscriptionLevels(Locale.getDefault())
      }
      .flatMap { it.flattenResult() }
      .map { levels -> levels.levels.values.map { Badges.fromServiceBadge(it.badge) } }
      .subscribeOn(Schedulers.io())

    disposables += Single.zip(giftBadges, boostBadges, subscriptionBadges) { g, b, s ->
      g + b + s
    }.subscribe { badges ->
      store.update { it.copy(badges = badges) }
    }
  }

  override fun onCleared() {
    disposables.clear()
    store.dispose()
  }

  fun setSelectedBadge(badgeIndex: Int) {
    store.update {
      it.copy(selectedBadge = if (badgeIndex in it.badges.indices) it.badges[badgeIndex] else null)
    }
  }

  fun setSelectedUnexpectedSubscriptionCancellation(unexpectedSubscriptionCancellationIndex: Int) {
    store.update {
      it.copy(
        selectedUnexpectedSubscriptionCancellation = if (unexpectedSubscriptionCancellationIndex in UnexpectedSubscriptionCancellation.values().indices) {
          UnexpectedSubscriptionCancellation.values()[unexpectedSubscriptionCancellationIndex]
        } else {
          null
        }
      )
    }
  }

  fun setStripeDeclineCode(stripeDeclineCodeIndex: Int) {
    store.update {
      it.copy(
        selectedStripeDeclineCode = if (stripeDeclineCodeIndex in StripeDeclineCode.Code.values().indices) {
          StripeDeclineCode.Code.values()[stripeDeclineCodeIndex]
        } else {
          null
        }
      )
    }
  }

  fun save(): Completable {
    val snapshot = store.state
    val saveState = Completable.fromAction {
      synchronized(SubscriptionReceiptRequestResponseJob.MUTEX) {
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
      synchronized(SubscriptionReceiptRequestResponseJob.MUTEX) {
        SignalStore.donationsValues().setExpiredBadge(null)
        SignalStore.donationsValues().setExpiredGiftBadge(null)
        SignalStore.donationsValues().unexpectedSubscriptionCancelationReason = null
        SignalStore.donationsValues().unexpectedSubscriptionCancelationTimestamp = 0L
        SignalStore.donationsValues().setUnexpectedSubscriptionCancelationChargeFailure(null)
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
    SignalStore.donationsValues().setExpiredBadge(state.selectedBadge)
  }

  private fun handleGiftExpiration(state: InternalDonorErrorConfigurationState) {
    SignalStore.donationsValues().setExpiredGiftBadge(state.selectedBadge)
  }

  private fun handleSubscriptionExpiration(state: InternalDonorErrorConfigurationState) {
    SignalStore.donationsValues().setExpiredBadge(state.selectedBadge)
    handleSubscriptionPaymentFailure(state)
  }

  private fun handleSubscriptionPaymentFailure(state: InternalDonorErrorConfigurationState) {
    SignalStore.donationsValues().unexpectedSubscriptionCancelationReason = state.selectedUnexpectedSubscriptionCancellation?.status
    SignalStore.donationsValues().unexpectedSubscriptionCancelationTimestamp = System.currentTimeMillis()
    SignalStore.donationsValues().setUnexpectedSubscriptionCancelationChargeFailure(
      state.selectedStripeDeclineCode?.let {
        ActiveSubscription.ChargeFailure(
          it.code,
          "Test Charge Failure",
          "Test Network Status",
          "Test Network Reason",
          "Test"
        )
      }
    )
  }
}
