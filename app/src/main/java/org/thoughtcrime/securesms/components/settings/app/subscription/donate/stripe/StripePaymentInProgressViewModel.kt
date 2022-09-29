package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.wallet.PaymentData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.logging.Log
import org.signal.donations.GooglePayPaymentSource
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayRequest
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceSubscriptionSyncRequestJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.subscriptions.SubscriptionLevels
import org.whispersystems.signalservice.api.util.Preconditions

class StripePaymentInProgressViewModel(
  private val donationPaymentRepository: DonationPaymentRepository
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(StripePaymentInProgressViewModel::class.java)
  }

  private val store = RxStore(StripeStage.INIT)
  val state: Flowable<StripeStage> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  private val disposables = CompositeDisposable()
  private var paymentData: PaymentData? = null

  override fun onCleared() {
    disposables.clear()
    store.dispose()
  }

  fun processNewDonation(request: GatewayRequest) {
    val paymentData = this.paymentData ?: error("Cannot process new donation without payment data")
    this.paymentData = null

    Preconditions.checkState(store.state != StripeStage.PAYMENT_PIPELINE)
    Log.d(TAG, "Proceeding with donation...")

    return when (request.donateToSignalType) {
      DonateToSignalType.MONTHLY -> proceedMonthly(request, paymentData)
      DonateToSignalType.ONE_TIME -> proceedOneTime(request, paymentData)
    }
  }

  fun providePaymentData(paymentData: PaymentData) {
    this.paymentData = paymentData
  }

  private fun proceedMonthly(request: GatewayRequest, paymentData: PaymentData) {
    val ensureSubscriberId = donationPaymentRepository.ensureSubscriberId()
    val continueSetup = donationPaymentRepository.continueSubscriptionSetup(GooglePayPaymentSource(paymentData))
    val setLevel = donationPaymentRepository.setSubscriptionLevel(request.level.toString())

    Log.d(TAG, "Starting subscription payment pipeline...", true)
    store.update { StripeStage.PAYMENT_PIPELINE }

    val setup = ensureSubscriberId
      .andThen(cancelActiveSubscriptionIfNecessary())
      .andThen(continueSetup)
      .onErrorResumeNext { Completable.error(DonationError.getPaymentSetupError(DonationErrorSource.SUBSCRIPTION, it)) }

    setup.andThen(setLevel).subscribeBy(
      onError = { throwable ->
        Log.w(TAG, "Failure in subscription payment pipeline...", throwable, true)
        store.update { StripeStage.FAILED }

        val donationError: DonationError = if (throwable is DonationError) {
          throwable
        } else {
          DonationError.genericBadgeRedemptionFailure(DonationErrorSource.SUBSCRIPTION)
        }
        DonationError.routeDonationError(ApplicationDependencies.getApplication(), donationError)
      },
      onComplete = {
        Log.d(TAG, "Finished subscription payment pipeline...", true)
        store.update { StripeStage.COMPLETE }
      }
    )
  }

  private fun cancelActiveSubscriptionIfNecessary(): Completable {
    return Single.just(SignalStore.donationsValues().shouldCancelSubscriptionBeforeNextSubscribeAttempt).flatMapCompletable {
      if (it) {
        Log.d(TAG, "Cancelling active subscription...", true)
        donationPaymentRepository.cancelActiveSubscription().doOnComplete {
          SignalStore.donationsValues().updateLocalStateForManualCancellation()
          MultiDeviceSubscriptionSyncRequestJob.enqueue()
        }
      } else {
        Completable.complete()
      }
    }
  }

  private fun proceedOneTime(request: GatewayRequest, paymentData: PaymentData) {
    Log.w(TAG, "Beginning one-time payment pipeline...", true)

    donationPaymentRepository.continuePayment(request.fiat, GooglePayPaymentSource(paymentData), Recipient.self().id, null, SubscriptionLevels.BOOST_LEVEL.toLong()).subscribeBy(
      onError = { throwable ->
        Log.w(TAG, "Failure in one-time payment pipeline...", throwable, true)
        store.update { StripeStage.FAILED }

        val donationError: DonationError = if (throwable is DonationError) {
          throwable
        } else {
          DonationError.genericBadgeRedemptionFailure(DonationErrorSource.BOOST)
        }
        DonationError.routeDonationError(ApplicationDependencies.getApplication(), donationError)
      },
      onComplete = {
        Log.w(TAG, "Completed one-time payment pipeline...", true)
        store.update { StripeStage.COMPLETE }
      }
    )
  }

  fun cancelSubscription() {
    store.update { StripeStage.CANCELLING }
    disposables += donationPaymentRepository.cancelActiveSubscription().subscribeBy(
      onComplete = {
        Log.d(TAG, "Cancellation succeeded", true)
        SignalStore.donationsValues().updateLocalStateForManualCancellation()
        MultiDeviceSubscriptionSyncRequestJob.enqueue()
        donationPaymentRepository.scheduleSyncForAccountRecordChange()
        store.update { StripeStage.COMPLETE }
      },
      onError = { throwable ->
        Log.w(TAG, "Cancellation failed", throwable, true)
        store.update { StripeStage.FAILED }
      }
    )
  }

  fun updateSubscription(request: GatewayRequest) {
    store.update { StripeStage.PAYMENT_PIPELINE }
    cancelActiveSubscriptionIfNecessary().andThen(donationPaymentRepository.setSubscriptionLevel(request.level.toString()))
      .subscribeBy(
        onComplete = {
          Log.w(TAG, "Completed subscription update", true)
          store.update { StripeStage.COMPLETE }
        },
        onError = { throwable ->
          Log.w(TAG, "Failed to update subscription", throwable, true)
          val donationError: DonationError = if (throwable is DonationError) {
            throwable
          } else {
            DonationError.genericBadgeRedemptionFailure(DonationErrorSource.SUBSCRIPTION)
          }
          DonationError.routeDonationError(ApplicationDependencies.getApplication(), donationError)

          store.update { StripeStage.FAILED }
        }
      )
  }

  class Factory(
    private val donationPaymentRepository: DonationPaymentRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StripePaymentInProgressViewModel(donationPaymentRepository)) as T
    }
  }
}
