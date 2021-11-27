package org.thoughtcrime.securesms.components.settings.app.subscription.subscribe

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.wallet.PaymentData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.GooglePayApi
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationEvent
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationExceptions
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.thoughtcrime.securesms.util.PlatformCurrencyUtil
import org.thoughtcrime.securesms.util.livedata.Store
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.util.Currency

class SubscribeViewModel(
  private val subscriptionsRepository: SubscriptionsRepository,
  private val donationPaymentRepository: DonationPaymentRepository,
  private val fetchTokenRequestCode: Int
) : ViewModel() {

  private val store = Store(SubscribeState(currencySelection = SignalStore.donationsValues().getSubscriptionCurrency()))
  private val eventPublisher: PublishSubject<DonationEvent> = PublishSubject.create()
  private val disposables = CompositeDisposable()
  private val networkDisposable: Disposable

  val state: LiveData<SubscribeState> = store.stateLiveData
  val events: Observable<DonationEvent> = eventPublisher.observeOn(AndroidSchedulers.mainThread())

  private var subscriptionToPurchase: Subscription? = null
  private val activeSubscriptionSubject = PublishSubject.create<ActiveSubscription>()

  init {
    networkDisposable = InternetConnectionObserver
      .observe()
      .distinctUntilChanged()
      .subscribe { isConnected ->
        if (isConnected) {
          retry()
        }
      }
  }

  override fun onCleared() {
    networkDisposable.dispose()
    disposables.dispose()
  }

  fun getPriceOfSelectedSubscription(): FiatMoney? {
    return store.state.selectedSubscription?.prices?.first { it.currency == store.state.currencySelection }
  }

  fun getSelectableCurrencyCodes(): List<String>? {
    return store.state.subscriptions.firstOrNull()?.prices?.map { it.currency.currencyCode }
  }

  fun retry() {
    if (!disposables.isDisposed && store.state.stage == SubscribeState.Stage.FAILURE) {
      store.update { it.copy(stage = SubscribeState.Stage.INIT) }
      refresh()
    }
  }

  fun refresh() {
    disposables.clear()

    val currency: Observable<Currency> = SignalStore.donationsValues().observableSubscriptionCurrency
    val allSubscriptions: Single<List<Subscription>> = subscriptionsRepository.getSubscriptions()

    refreshActiveSubscription()

    disposables += LevelUpdate.isProcessing.subscribeBy {
      store.update { state ->
        state.copy(
          hasInProgressSubscriptionTransaction = it
        )
      }
    }

    disposables += allSubscriptions.subscribeBy(
      onSuccess = { subscriptions ->
        if (subscriptions.isNotEmpty()) {
          val priceCurrencies = subscriptions[0].prices.map { it.currency }
          val selectedCurrency = SignalStore.donationsValues().getSubscriptionCurrency()

          if (selectedCurrency !in priceCurrencies) {
            Log.w(TAG, "Unsupported currency selection. Defaulting to USD. $currency isn't supported.")
            val usd = PlatformCurrencyUtil.USD
            val newSubscriber = SignalStore.donationsValues().getSubscriber(usd) ?: Subscriber(SubscriberId.generate(), usd.currencyCode)
            SignalStore.donationsValues().setSubscriber(newSubscriber)
            donationPaymentRepository.scheduleSyncForAccountRecordChange()
          }
        }
      },
      onError = {}
    )

    disposables += Observable.combineLatest(allSubscriptions.toObservable(), activeSubscriptionSubject, ::Pair).subscribeBy(
      onNext = { (subs, active) ->
        store.update {
          it.copy(
            subscriptions = subs,
            selectedSubscription = it.selectedSubscription ?: resolveSelectedSubscription(active, subs),
            activeSubscription = active,
            stage = if (it.stage == SubscribeState.Stage.INIT || it.stage == SubscribeState.Stage.FAILURE) SubscribeState.Stage.READY else it.stage,
          )
        }
      },
      onError = this::handleSubscriptionDataLoadFailure
    )

    disposables += donationPaymentRepository.isGooglePayAvailable().subscribeBy(
      onComplete = { store.update { it.copy(isGooglePayAvailable = true) } },
      onError = { eventPublisher.onNext(DonationEvent.GooglePayUnavailableError(it)) }
    )

    disposables += currency.subscribe { selection ->
      store.update { it.copy(currencySelection = selection) }
    }
  }

  private fun handleSubscriptionDataLoadFailure(throwable: Throwable) {
    Log.w(TAG, "Could not load subscription data", throwable)
    store.update {
      it.copy(stage = SubscribeState.Stage.FAILURE)
    }
  }

  fun refreshActiveSubscription() {
    subscriptionsRepository
      .getActiveSubscription()
      .subscribeBy(
        onSuccess = { activeSubscriptionSubject.onNext(it) },
        onError = { activeSubscriptionSubject.onNext(ActiveSubscription(null)) }
      )
  }

  private fun resolveSelectedSubscription(activeSubscription: ActiveSubscription, subscriptions: List<Subscription>): Subscription? {
    return if (activeSubscription.isActive) {
      subscriptions.firstOrNull { it.level == activeSubscription.activeSubscription.level }
    } else {
      subscriptions.firstOrNull()
    }
  }

  private fun cancelActiveSubscriptionIfNecessary(): Completable {
    return Single.just(SignalStore.donationsValues().shouldCancelSubscriptionBeforeNextSubscribeAttempt).flatMapCompletable {
      if (it) {
        donationPaymentRepository.cancelActiveSubscription().doOnComplete {
          SignalStore.donationsValues().setLastEndOfPeriod(0L)
          SignalStore.donationsValues().clearLevelOperations()
          SignalStore.donationsValues().shouldCancelSubscriptionBeforeNextSubscribeAttempt = false
        }
      } else {
        Completable.complete()
      }
    }
  }

  fun cancel() {
    store.update { it.copy(stage = SubscribeState.Stage.CANCELLING) }
    disposables += donationPaymentRepository.cancelActiveSubscription().subscribeBy(
      onComplete = {
        eventPublisher.onNext(DonationEvent.SubscriptionCancelled)
        SignalStore.donationsValues().setLastEndOfPeriod(0L)
        SignalStore.donationsValues().clearLevelOperations()
        SignalStore.donationsValues().markUserManuallyCancelled()
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

            val setup = ensureSubscriberId
              .andThen(cancelActiveSubscriptionIfNecessary())
              .andThen(continueSetup)
              .onErrorResumeNext { Completable.error(DonationExceptions.SetupFailed(it)) }

            setup.andThen(setLevel).subscribeBy(
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

        override fun onError(googlePayException: GooglePayApi.GooglePayException) {
          store.update { it.copy(stage = SubscribeState.Stage.READY) }
          eventPublisher.onNext(DonationEvent.RequestTokenError(googlePayException))
        }

        override fun onCancelled() {
          store.update { it.copy(stage = SubscribeState.Stage.READY) }
        }
      }
    )
  }

  fun updateSubscription() {
    store.update { it.copy(stage = SubscribeState.Stage.PAYMENT_PIPELINE) }
    cancelActiveSubscriptionIfNecessary().andThen(donationPaymentRepository.setSubscriptionLevel(store.state.selectedSubscription!!.level.toString()))
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

    val selectedCurrency = snapshot.currencySelection

    subscriptionToPurchase = snapshot.selectedSubscription
    donationPaymentRepository.requestTokenFromGooglePay(snapshot.selectedSubscription.prices.first { it.currency == selectedCurrency }, snapshot.selectedSubscription.name, fetchTokenRequestCode)
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

  companion object {
    private val TAG = Log.tag(SubscribeViewModel::class.java)
  }
}
