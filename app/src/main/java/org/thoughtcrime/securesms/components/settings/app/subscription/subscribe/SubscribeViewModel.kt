package org.thoughtcrime.securesms.components.settings.app.subscription.subscribe

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.wallet.PaymentData
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.donations.GooglePayApi
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationEvent
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.models.CurrencySelection
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.livedata.Store
import org.whispersystems.libsignal.util.guava.Optional

class SubscribeViewModel(
  private val subscriptionsRepository: SubscriptionsRepository,
  private val donationPaymentRepository: DonationPaymentRepository,
  private val fetchTokenRequestCode: Int
) : ViewModel() {

  private val store = Store(SubscribeState())
  private val eventPublisher: PublishSubject<DonationEvent> = PublishSubject.create()
  private val disposables = CompositeDisposable()

  val state: LiveData<SubscribeState> = store.stateLiveData
  val events: Observable<DonationEvent> = eventPublisher

  private var subscriptionToPurchase: Subscription? = null

  override fun onCleared() {
    disposables.clear()
  }

  fun refresh() {
    disposables.clear()

    val currency = SignalStore.donationsValues().getCurrency()

    val allSubscriptions = subscriptionsRepository.getSubscriptions(currency)
    val activeSubscription = subscriptionsRepository.getActiveSubscription(currency)
      .map { Optional.of(it) }
      .defaultIfEmpty(Optional.absent())

    disposables += allSubscriptions.zipWith(activeSubscription, ::Pair).subscribe { (subs, active) ->
      store.update {
        it.copy(
          subscriptions = subs,
          selectedSubscription = it.selectedSubscription ?: active.orNull() ?: subs.firstOrNull(),
          activeSubscription = active.orNull(),
          stage = SubscribeState.Stage.READY
        )
      }
    }

    disposables += donationPaymentRepository.isGooglePayAvailable().subscribeBy(
      onComplete = { store.update { it.copy(isGooglePayAvailable = true) } },
      onError = { eventPublisher.onNext(DonationEvent.GooglePayUnavailableError(it)) }
    )

    store.update { it.copy(currencySelection = CurrencySelection(SignalStore.donationsValues().getCurrency().currencyCode)) }
  }
  fun cancel() {
    store.update { it.copy(stage = SubscribeState.Stage.CANCELLING) }
    // TODO [alex] -- cancel api call
  }

  fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    donationPaymentRepository.onActivityResult(
      requestCode, resultCode, data, this.fetchTokenRequestCode,
      object : GooglePayApi.PaymentRequestCallback {
        override fun onSuccess(paymentData: PaymentData) {
          val subscription = subscriptionToPurchase
          subscriptionToPurchase = null

          if (subscription != null) {
            eventPublisher.onNext(DonationEvent.RequestTokenSuccess)
            donationPaymentRepository.continuePayment(subscription.price, paymentData).subscribeBy(
              onError = { eventPublisher.onNext(DonationEvent.PaymentConfirmationError(it)) },
              onComplete = {
                // Now we need to do the whole query for a token, submit token rigamarole
                eventPublisher.onNext(DonationEvent.PaymentConfirmationSuccess(subscription.badge))
              }
            )
          }
        }

        override fun onError() {
          store.update { it.copy(stage = SubscribeState.Stage.PAYMENT_PIPELINE) }
          eventPublisher.onNext(DonationEvent.RequestTokenError)
        }

        override fun onCancelled() {
          store.update { it.copy(stage = SubscribeState.Stage.PAYMENT_PIPELINE) }
        }
      }
    )
  }

  fun requestTokenFromGooglePay() {
    val snapshot = store.state
    if (snapshot.selectedSubscription == null) {
      return
    }

    store.update { it.copy(stage = SubscribeState.Stage.PAYMENT_PIPELINE) }

    subscriptionToPurchase = snapshot.selectedSubscription
    donationPaymentRepository.requestTokenFromGooglePay(snapshot.selectedSubscription.price, snapshot.selectedSubscription.title, fetchTokenRequestCode)
  }

  fun setSelectedSubscription(subscription: Subscription) {
    store.update { it.copy(selectedSubscription = subscription) }
  }

  class Factory(
    private val subscriptionsRepository: SubscriptionsRepository,
    private val donationPaymentRepository: DonationPaymentRepository,
    private val fetchTokenRequestCode: Int
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(SubscribeViewModel(subscriptionsRepository, donationPaymentRepository, fetchTokenRequestCode))!!
    }
  }
}
