package org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.OneTimeInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.PayPalRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorStage
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceSubscriptionSyncRequestJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentIntentResponse
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentMethodResponse
import org.whispersystems.signalservice.api.util.Preconditions

class PayPalPaymentInProgressViewModel(
  private val payPalRepository: PayPalRepository,
  private val oneTimeInAppPaymentRepository: OneTimeInAppPaymentRepository
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(PayPalPaymentInProgressViewModel::class.java)
  }

  private val store = RxStore(InAppPaymentProcessorStage.INIT)
  val state: Flowable<InAppPaymentProcessorStage> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  private val disposables = CompositeDisposable()
  override fun onCleared() {
    store.dispose()
    disposables.clear()
  }

  fun onBeginNewAction() {
    Preconditions.checkState(!store.state.isInProgress)

    Log.d(TAG, "Beginning a new action. Ensuring cleared state.", true)
    disposables.clear()
  }

  fun onEndAction() {
    Preconditions.checkState(store.state.isTerminal)

    Log.d(TAG, "Ending current state. Clearing state and setting stage to INIT", true)
    store.update { InAppPaymentProcessorStage.INIT }
    disposables.clear()
  }

  fun processNewDonation(
    inAppPayment: InAppPaymentTable.InAppPayment,
    routeToOneTimeConfirmation: (PayPalCreatePaymentIntentResponse) -> Single<PayPalConfirmationResult>,
    routeToMonthlyConfirmation: (PayPalCreatePaymentMethodResponse) -> Single<PayPalPaymentMethodId>
  ) {
    Log.d(TAG, "Proceeding with donation...", true)

    return if (inAppPayment.type.recurring) {
      proceedMonthly(inAppPayment, routeToMonthlyConfirmation)
    } else {
      proceedOneTime(inAppPayment, routeToOneTimeConfirmation)
    }
  }

  fun updateSubscription(inAppPayment: InAppPaymentTable.InAppPayment) {
    Log.d(TAG, "Beginning subscription update...", true)

    store.update { InAppPaymentProcessorStage.PAYMENT_PIPELINE }
    disposables += RecurringInAppPaymentRepository.cancelActiveSubscriptionIfNecessary(inAppPayment.type.requireSubscriberType()).andThen(RecurringInAppPaymentRepository.setSubscriptionLevel(inAppPayment, PaymentSourceType.PayPal))
      .subscribeBy(
        onComplete = {
          Log.w(TAG, "Completed subscription update", true)
          store.update { InAppPaymentProcessorStage.COMPLETE }
        },
        onError = { throwable ->
          Log.w(TAG, "Failed to update subscription", throwable, true)
          store.update { InAppPaymentProcessorStage.FAILED }
          InAppPaymentsRepository.handlePipelineError(inAppPayment.id, DonationErrorSource.MONTHLY, PaymentSourceType.PayPal, throwable)
        }
      )
  }

  fun cancelSubscription(subscriberType: InAppPaymentSubscriberRecord.Type) {
    Log.d(TAG, "Beginning cancellation...", true)

    store.update { InAppPaymentProcessorStage.CANCELLING }
    disposables += RecurringInAppPaymentRepository.cancelActiveSubscription(subscriberType).subscribeBy(
      onComplete = {
        Log.d(TAG, "Cancellation succeeded", true)
        SignalStore.inAppPayments.updateLocalStateForManualCancellation(subscriberType)
        MultiDeviceSubscriptionSyncRequestJob.enqueue()
        RecurringInAppPaymentRepository.syncAccountRecord().subscribe()
        store.update { InAppPaymentProcessorStage.COMPLETE }
      },
      onError = { throwable ->
        Log.w(TAG, "Cancellation failed", throwable, true)
        store.update { InAppPaymentProcessorStage.FAILED }
      }
    )
  }

  private fun proceedOneTime(
    inAppPayment: InAppPaymentTable.InAppPayment,
    routeToPaypalConfirmation: (PayPalCreatePaymentIntentResponse) -> Single<PayPalConfirmationResult>
  ) {
    Log.d(TAG, "Proceeding with one-time payment pipeline...", true)
    store.update { InAppPaymentProcessorStage.PAYMENT_PIPELINE }
    val verifyUser = if (inAppPayment.type == InAppPaymentType.ONE_TIME_GIFT) {
      OneTimeInAppPaymentRepository.verifyRecipientIsAllowedToReceiveAGift(RecipientId.from(inAppPayment.data.recipientId!!))
    } else {
      Completable.complete()
    }

    disposables += verifyUser
      .andThen(
        payPalRepository
          .createOneTimePaymentIntent(
            amount = inAppPayment.data.amount!!.toFiatMoney(),
            badgeRecipient = inAppPayment.data.recipientId?.let { RecipientId.from(it) } ?: Recipient.self().id,
            badgeLevel = inAppPayment.data.level
          )
      )
      .flatMap(routeToPaypalConfirmation)
      .flatMap { result ->
        payPalRepository.confirmOneTimePaymentIntent(
          amount = inAppPayment.data.amount.toFiatMoney(),
          badgeLevel = inAppPayment.data.level,
          paypalConfirmationResult = result
        )
      }
      .flatMapCompletable { response ->
        oneTimeInAppPaymentRepository.waitForOneTimeRedemption(
          inAppPayment = inAppPayment,
          paymentIntentId = response.paymentId,
          paymentSourceType = PaymentSourceType.PayPal
        )
      }
      .subscribeOn(Schedulers.io())
      .subscribeBy(
        onError = { throwable ->
          Log.w(TAG, "Failure in one-time payment pipeline...", throwable, true)
          store.update { InAppPaymentProcessorStage.FAILED }
          InAppPaymentsRepository.handlePipelineError(inAppPayment.id, DonationErrorSource.ONE_TIME, PaymentSourceType.PayPal, throwable)
        },
        onComplete = {
          Log.d(TAG, "Finished one-time payment pipeline...", true)
          store.update { InAppPaymentProcessorStage.COMPLETE }
        }
      )
  }

  private fun proceedMonthly(inAppPayment: InAppPaymentTable.InAppPayment, routeToPaypalConfirmation: (PayPalCreatePaymentMethodResponse) -> Single<PayPalPaymentMethodId>) {
    Log.d(TAG, "Proceeding with monthly payment pipeline for InAppPayment::${inAppPayment.id} of type ${inAppPayment.type}...", true)

    val setup = RecurringInAppPaymentRepository.ensureSubscriberId(inAppPayment.type.requireSubscriberType())
      .andThen(RecurringInAppPaymentRepository.cancelActiveSubscriptionIfNecessary(inAppPayment.type.requireSubscriberType()))
      .andThen(payPalRepository.createPaymentMethod(inAppPayment.type.requireSubscriberType()))
      .flatMap(routeToPaypalConfirmation)
      .flatMapCompletable { payPalRepository.setDefaultPaymentMethod(inAppPayment.type.requireSubscriberType(), it.paymentId) }
      .onErrorResumeNext { Completable.error(DonationError.getPaymentSetupError(DonationErrorSource.MONTHLY, it, PaymentSourceType.PayPal)) }

    disposables += setup.andThen(RecurringInAppPaymentRepository.setSubscriptionLevel(inAppPayment, PaymentSourceType.PayPal))
      .subscribeBy(
        onError = { throwable ->
          Log.w(TAG, "Failure in monthly payment pipeline...", throwable, true)
          store.update { InAppPaymentProcessorStage.FAILED }
          InAppPaymentsRepository.handlePipelineError(inAppPayment.id, DonationErrorSource.MONTHLY, PaymentSourceType.PayPal, throwable)
        },
        onComplete = {
          Log.d(TAG, "Finished subscription payment pipeline...", true)
          store.update { InAppPaymentProcessorStage.COMPLETE }
        }
      )
  }

  class Factory(
    private val payPalRepository: PayPalRepository = PayPalRepository(AppDependencies.donationsService),
    private val oneTimeInAppPaymentRepository: OneTimeInAppPaymentRepository = OneTimeInAppPaymentRepository(AppDependencies.donationsService)
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(PayPalPaymentInProgressViewModel(payPalRepository, oneTimeInAppPaymentRepository)) as T
    }
  }
}
