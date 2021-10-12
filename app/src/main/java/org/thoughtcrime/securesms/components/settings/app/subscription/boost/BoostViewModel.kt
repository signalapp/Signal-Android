package org.thoughtcrime.securesms.components.settings.app.subscription.boost

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
import org.signal.core.util.money.FiatMoney
import org.signal.donations.GooglePayApi
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationEvent
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.models.CurrencySelection
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.livedata.Store
import java.math.BigDecimal

class BoostViewModel(
  private val boostRepository: BoostRepository,
  private val donationPaymentRepository: DonationPaymentRepository,
  private val fetchTokenRequestCode: Int
) : ViewModel() {

  private val store = Store(BoostState())
  private val eventPublisher: PublishSubject<DonationEvent> = PublishSubject.create()
  private val disposables = CompositeDisposable()

  val state: LiveData<BoostState> = store.stateLiveData
  val events: Observable<DonationEvent> = eventPublisher

  private var boostToPurchase: Boost? = null

  override fun onCleared() {
    disposables.clear()
  }

  init {
    val currencyObservable = SignalStore.donationsValues().observableCurrency
    val boosts = currencyObservable.flatMapSingle { boostRepository.getBoosts(it) }
    val boostBadge = boostRepository.getBoostBadge()

    disposables += Observable.combineLatest(boosts, boostBadge.toObservable()) { (boosts, defaultBoost), badge -> BoostInfo(boosts, defaultBoost, badge) }.subscribe { info ->
      store.update {
        it.copy(
          boosts = info.boosts,
          selectedBoost = if (it.selectedBoost in info.boosts) it.selectedBoost else info.defaultBoost,
          boostBadge = it.boostBadge ?: info.boostBadge,
          stage = if (it.stage == BoostState.Stage.INIT) BoostState.Stage.READY else it.stage
        )
      }
    }

    disposables += donationPaymentRepository.isGooglePayAvailable().subscribeBy(
      onComplete = { store.update { it.copy(isGooglePayAvailable = true) } },
      onError = { eventPublisher.onNext(DonationEvent.GooglePayUnavailableError(it)) }
    )

    disposables += currencyObservable.subscribeBy { currency ->
      store.update {
        it.copy(
          currencySelection = CurrencySelection(currency.currencyCode),
          isCustomAmountFocused = false,
          customAmount = FiatMoney(
            BigDecimal.ZERO, currency
          )
        )
      }
    }
  }

  fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    donationPaymentRepository.onActivityResult(
      requestCode,
      resultCode,
      data,
      this.fetchTokenRequestCode,
      object : GooglePayApi.PaymentRequestCallback {
        override fun onSuccess(paymentData: PaymentData) {
          val boost = boostToPurchase
          boostToPurchase = null

          if (boost != null) {
            eventPublisher.onNext(DonationEvent.RequestTokenSuccess)
            donationPaymentRepository.continuePayment(boost.price, paymentData).subscribeBy(
              onError = { eventPublisher.onNext(DonationEvent.PaymentConfirmationError(it)) },
              onComplete = {
                // Now we need to do the whole query for a token, submit token rigamarole
                eventPublisher.onNext(DonationEvent.PaymentConfirmationSuccess(store.state.boostBadge!!))
              }
            )
          }
        }

        override fun onError() {
          store.update { it.copy(stage = BoostState.Stage.PAYMENT_PIPELINE) }
          eventPublisher.onNext(DonationEvent.RequestTokenError)
        }

        override fun onCancelled() {
          store.update { it.copy(stage = BoostState.Stage.PAYMENT_PIPELINE) }
        }
      }
    )
  }

  fun requestTokenFromGooglePay(label: String) {
    val snapshot = store.state
    if (snapshot.selectedBoost == null) {
      return
    }

    store.update { it.copy(stage = BoostState.Stage.PAYMENT_PIPELINE) }

    // TODO [alex] -- Do we want prevalidation? Stripe will catch us anyway.
    // TODO [alex] -- Custom boost badge details... how do we determine this?
    boostToPurchase = if (snapshot.isCustomAmountFocused) {
      Boost(snapshot.selectedBoost.badge, snapshot.customAmount)
    } else {
      snapshot.selectedBoost
    }

    donationPaymentRepository.requestTokenFromGooglePay(snapshot.selectedBoost.price, label, fetchTokenRequestCode)
  }

  fun setSelectedBoost(boost: Boost) {
    store.update {
      it.copy(
        isCustomAmountFocused = false,
        selectedBoost = boost
      )
    }
  }

  fun setCustomAmount(amount: String) {
    val bigDecimalAmount = if (amount.isEmpty()) {
      BigDecimal.ZERO
    } else {
      BigDecimal(amount)
    }

    store.update { it.copy(customAmount = FiatMoney(bigDecimalAmount, it.customAmount.currency)) }
  }

  fun setCustomAmountFocused(isFocused: Boolean) {
    store.update { it.copy(isCustomAmountFocused = isFocused) }
  }

  private data class BoostInfo(val boosts: List<Boost>, val defaultBoost: Boost?, val boostBadge: Badge)

  class Factory(
    private val boostRepository: BoostRepository,
    private val donationPaymentRepository: DonationPaymentRepository,
    private val fetchTokenRequestCode: Int
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(BoostViewModel(boostRepository, donationPaymentRepository, fetchTokenRequestCode))!!
    }
  }
}
