package org.thoughtcrime.securesms.components.settings.app.subscription.subscribe

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.wallet.PaymentData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
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
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.util.Currency

class SubscribeViewModel(
  private val subscriptionsRepository: SubscriptionsRepository,
  private val donationPaymentRepository: DonationPaymentRepository,
  private val fetchTokenRequestCode: Int
) : ViewModel() {

  private val store = Store(SubscribeState())
  private val eventPublisher: PublishSubject<DonationEvent> = PublishSubject.create()
  private val disposables = CompositeDisposable()

  val state: LiveData<SubscribeState> = store.stateLiveData
  val events: Observable<DonationEvent> = eventPublisher.observeOn(AndroidSchedulers.mainThread())

  private var subscriptionToPurchase: Subscription? = null
  private val activeSubscriptionSubject = PublishSubject.create<ActiveSubscription>()

  override fun onCleared() {
    disposables.clear()
  }

  init {
    val currency: Observable<Currency> = SignalStore.donationsValues().observableSubscriptionCurrency
    val allSubscriptions: Observable<List<Subscription>> = currency.switchMapSingle { subscriptionsRepository.getSubscriptions(it) }
    refreshActiveSubscription()

    disposables += SignalStore.donationsValues().levelUpdateOperationObservable.subscribeBy {
      store.update { state ->
        state.copy(
          hasInProgressSubscriptionTransaction = it.isPresent
        )
      }
    }

    disposables += Observable.combineLatest(allSubscriptions, activeSubscriptionSubject, ::Pair).subscribe { (subs, active) ->
      store.update {
        it.copy(
          subscriptions = subs,
          selectedSubscription = it.selectedSubscription ?: resolveSelectedSubscription(active, subs),
          activeSubscription = active,
          stage = if (it.stage == SubscribeState.Stage.INIT) SubscribeState.Stage.READY else it.stage,
        )
      }
    }

    disposables += donationPaymentRepository.isGooglePayAvailable().subscribeBy(
      onComplete = { store.update { it.copy(isGooglePayAvailable = true) } },
      onError = { eventPublisher.onNext(DonationEvent.GooglePayUnavailableError(it)) }
    )

    disposables += currency.map { CurrencySelection(it.currencyCode) }.subscribe { selection ->
      store.update { it.copy(currencySelection = selection) }
    }
  }

  fun refreshActiveSubscription() {
    subscriptionsRepository
      .getActiveSubscription()
      .subscribeBy { activeSubscriptionSubject.onNext(it) }
  }

  private fun resolveSelectedSubscription(activeSubscription: ActiveSubscription, subscriptions: List<Subscription>): Subscription? {
    return if (activeSubscription.isActive) {
      subscriptions.firstOrNull { it.level == activeSubscription.activeSubscription.level }
    } else {
      subscriptions.firstOrNull()
    }
  }

  fun cancel() {
    store.update { it.copy(stage = SubscribeState.Stage.CANCELLING) }
    disposables += donationPaymentRepository.cancelActiveSubscription().subscribeBy(
      onComplete = {
        eventPublisher.onNext(DonationEvent.SubscriptionCancelled)
        SignalStore.donationsValues().setLastEndOfPeriod(0L)
        refreshActiveSubscription()
        store.update { it.copy(stage = SubscribeState.Stage.READY) }
      },
      onError = { throwable ->
        eventPublisher.onNext(DonationEvent.SubscriptionCancellationFailed(throwable))
        store.update { it.copy(stage = SubscribeState.Stage.READY) }
      }
    )
  }

  fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    val subscription = subscriptionToPurchase
    subscriptionToPurchase = null

    donationPaymentRepository.onActivityResult(
      requestCode, resultCode, data, this.fetchTokenRequestCode,
      object : GooglePayApi.PaymentRequestCallback {
        override fun onSuccess(paymentData: PaymentData) {
          if (subscription != null) {
            eventPublisher.onNext(DonationEvent.RequestTokenSuccess)

            val ensureSubscriberId = donationPaymentRepository.ensureSubscriberId()
            val continueSetup = donationPaymentRepository.continueSubscriptionSetup(paymentData)
            val setLevel = donationPaymentRepository.setSubscriptionLevel(subscription.level.toString())

            store.update { it.copy(stage = SubscribeState.Stage.PAYMENT_PIPELINE) }

            ensureSubscriberId.andThen(continueSetup).andThen(setLevel).subscribeBy(
              onError = { throwable ->
                refreshActiveSubscription()
                store.update { it.copy(stage = SubscribeState.Stage.READY) }
                eventPublisher.onNext(DonationEvent.PaymentConfirmationError(throwable))
              },
              onComplete = {
                store.update { it.copy(stage = SubscribeState.Stage.READY) }
                eventPublisher.onNext(DonationEvent.PaymentConfirmationSuccess(subscription.badge))
              }
            )
          } else {
            store.update { it.copy(stage = SubscribeState.Stage.READY) }
          }
        }

        override fun onError() {
          store.update { it.copy(stage = SubscribeState.Stage.READY) }
          eventPublisher.onNext(DonationEvent.RequestTokenError)
        }

        override fun onCancelled() {
          store.update { it.copy(stage = SubscribeState.Stage.READY) }
        }
      }
    )
  }

  fun updateSubscription() {
    store.update { it.copy(stage = SubscribeState.Stage.PAYMENT_PIPELINE) }
    donationPaymentRepository.setSubscriptionLevel(store.state.selectedSubscription!!.level.toString())
      .subscribeBy(
        onComplete = {
          store.update { it.copy(stage = SubscribeState.Stage.READY) }
          eventPublisher.onNext(DonationEvent.PaymentConfirmationSuccess(store.state.selectedSubscription!!.badge))
        },
        onError = { throwable ->
          store.update { it.copy(stage = SubscribeState.Stage.READY) }
          eventPublisher.onNext(DonationEvent.PaymentConfirmationError(throwable))
        }
      )
  }

  fun requestTokenFromGooglePay() {
    val snapshot = store.state
    if (snapshot.selectedSubscription == null) {
      return
    }

    store.update { it.copy(stage = SubscribeState.Stage.TOKEN_REQUEST) }

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
