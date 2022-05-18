package org.thoughtcrime.securesms.badges.gifts.flow

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.wallet.PaymentData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.GooglePayApi
import org.thoughtcrime.securesms.badges.gifts.Gifts
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationEvent
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.thoughtcrime.securesms.util.rx.RxStore
import java.util.Currency

/**
 * Maintains state as a user works their way through the gift flow.
 */
class GiftFlowViewModel(
  val repository: GiftFlowRepository,
  val donationPaymentRepository: DonationPaymentRepository
) : ViewModel() {

  private var giftToPurchase: Gift? = null

  private val store = RxStore(
    GiftFlowState(
      currency = SignalStore.donationsValues().getOneTimeCurrency()
    )
  )
  private val disposables = CompositeDisposable()
  private val eventPublisher: PublishSubject<DonationEvent> = PublishSubject.create()
  private val networkDisposable: Disposable

  val state: Flowable<GiftFlowState> = store.stateFlowable
  val events: Observable<DonationEvent> = eventPublisher
  val snapshot: GiftFlowState get() = store.state

  init {
    refresh()

    networkDisposable = InternetConnectionObserver
      .observe()
      .distinctUntilChanged()
      .subscribe { isConnected ->
        if (isConnected) {
          retry()
        }
      }
  }

  fun retry() {
    if (!disposables.isDisposed && store.state.stage == GiftFlowState.Stage.FAILURE) {
      store.update { it.copy(stage = GiftFlowState.Stage.INIT) }
      refresh()
    }
  }

  fun refresh() {
    disposables.clear()
    disposables += SignalStore.donationsValues().observableOneTimeCurrency.subscribe { currency ->
      store.update {
        it.copy(
          currency = currency
        )
      }
    }

    disposables += repository.getGiftPricing().subscribe { giftPrices ->
      store.update {
        it.copy(
          giftPrices = giftPrices,
          stage = getLoadState(it, giftPrices = giftPrices)
        )
      }
    }

    disposables += repository.getGiftBadge().subscribeBy(
      onSuccess = { (giftLevel, giftBadge) ->
        store.update {
          it.copy(
            giftLevel = giftLevel,
            giftBadge = giftBadge,
            stage = getLoadState(it, giftBadge = giftBadge)
          )
        }
      },
      onError = { throwable ->
        Log.w(TAG, "Could not load gift badge", throwable)
        store.update {
          it.copy(
            stage = GiftFlowState.Stage.FAILURE
          )
        }
      }
    )
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun setSelectedContact(selectedContact: ContactSearchKey.RecipientSearchKey) {
    store.update {
      it.copy(recipient = Recipient.resolved(selectedContact.recipientId))
    }
  }

  fun getSupportedCurrencyCodes(): List<String> {
    return store.state.giftPrices.keys.map { it.currencyCode }
  }

  fun requestTokenFromGooglePay(label: String) {
    val giftLevel = store.state.giftLevel ?: return
    val giftPrice = store.state.giftPrices[store.state.currency] ?: return
    val giftRecipient = store.state.recipient?.id ?: return

    this.giftToPurchase = Gift(giftLevel, giftPrice)

    store.update { it.copy(stage = GiftFlowState.Stage.RECIPIENT_VERIFICATION) }
    disposables += donationPaymentRepository.verifyRecipientIsAllowedToReceiveAGift(giftRecipient)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onComplete = {
          store.update { it.copy(stage = GiftFlowState.Stage.TOKEN_REQUEST) }
          donationPaymentRepository.requestTokenFromGooglePay(giftToPurchase!!.price, label, Gifts.GOOGLE_PAY_REQUEST_CODE)
        },
        onError = this::onPaymentFlowError
      )
  }

  fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    val gift = giftToPurchase
    giftToPurchase = null

    val recipient = store.state.recipient?.id

    donationPaymentRepository.onActivityResult(
      requestCode, resultCode, data, Gifts.GOOGLE_PAY_REQUEST_CODE,
      object : GooglePayApi.PaymentRequestCallback {
        override fun onSuccess(paymentData: PaymentData) {
          if (gift != null && recipient != null) {
            eventPublisher.onNext(DonationEvent.RequestTokenSuccess)

            store.update { it.copy(stage = GiftFlowState.Stage.PAYMENT_PIPELINE) }

            donationPaymentRepository.continuePayment(gift.price, paymentData, recipient, store.state.additionalMessage?.toString(), gift.level).subscribeBy(
              onError = this@GiftFlowViewModel::onPaymentFlowError,
              onComplete = {
                store.update { it.copy(stage = GiftFlowState.Stage.READY) }
                eventPublisher.onNext(DonationEvent.PaymentConfirmationSuccess(store.state.giftBadge!!))
              }
            )
          } else {
            store.update { it.copy(stage = GiftFlowState.Stage.READY) }
          }
        }

        override fun onError(googlePayException: GooglePayApi.GooglePayException) {
          store.update { it.copy(stage = GiftFlowState.Stage.READY) }
          DonationError.routeDonationError(ApplicationDependencies.getApplication(), DonationError.getGooglePayRequestTokenError(DonationErrorSource.GIFT, googlePayException))
        }

        override fun onCancelled() {
          store.update { it.copy(stage = GiftFlowState.Stage.READY) }
        }
      }
    )
  }

  private fun onPaymentFlowError(throwable: Throwable) {
    store.update { it.copy(stage = GiftFlowState.Stage.READY) }
    val donationError: DonationError = if (throwable is DonationError) {
      throwable
    } else {
      Log.w(TAG, "Failed to complete payment or redemption", throwable, true)
      DonationError.genericBadgeRedemptionFailure(DonationErrorSource.GIFT)
    }
    DonationError.routeDonationError(ApplicationDependencies.getApplication(), donationError)
  }

  private fun getLoadState(
    oldState: GiftFlowState,
    giftPrices: Map<Currency, FiatMoney>? = null,
    giftBadge: Badge? = null,
  ): GiftFlowState.Stage {
    if (oldState.stage != GiftFlowState.Stage.INIT) {
      return oldState.stage
    }

    if (giftPrices?.isNotEmpty() == true) {
      return if (oldState.giftBadge != null) {
        GiftFlowState.Stage.READY
      } else {
        GiftFlowState.Stage.INIT
      }
    }

    if (giftBadge != null) {
      return if (oldState.giftPrices.isNotEmpty()) {
        GiftFlowState.Stage.READY
      } else {
        GiftFlowState.Stage.INIT
      }
    }

    return GiftFlowState.Stage.INIT
  }

  fun setAdditionalMessage(additionalMessage: CharSequence) {
    store.update { it.copy(additionalMessage = additionalMessage) }
  }

  companion object {
    private val TAG = Log.tag(GiftFlowViewModel::class.java)
  }

  class Factory(
    private val repository: GiftFlowRepository,
    private val donationPaymentRepository: DonationPaymentRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(
        GiftFlowViewModel(
          repository,
          donationPaymentRepository
        )
      ) as T
    }
  }
}
