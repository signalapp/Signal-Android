package org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.SingleSource
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.donations.PayPalPaymentSource
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.PayPalRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorStage
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.RequiredActionHandler
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.SharedInAppPaymentPipeline
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceSubscriptionSyncRequestJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.util.Preconditions

class PayPalPaymentInProgressViewModel(
  private val payPalRepository: PayPalRepository
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

  fun updateSubscription(inAppPayment: InAppPaymentTable.InAppPayment) {
    Log.d(TAG, "Beginning subscription update...", true)

    store.update { InAppPaymentProcessorStage.PAYMENT_PIPELINE }
    disposables += RecurringInAppPaymentRepository.cancelActiveSubscriptionIfNecessary(inAppPayment.type.requireSubscriberType()).andThen(
      SingleSource<InAppPaymentTable.InAppPayment> {
        val freshPayment = SignalDatabase.inAppPayments.moveToTransacting(inAppPayment.id)!!
        RecurringInAppPaymentRepository.setSubscriptionLevelSync(freshPayment)
      }
    ).flatMapCompletable {
      SharedInAppPaymentPipeline.awaitRedemption(it, PaymentSourceType.PayPal)
    }.subscribeBy(
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

  fun processNewDonation(inAppPayment: InAppPaymentTable.InAppPayment, requiredActionHandler: RequiredActionHandler) {
    Log.d(TAG, "Proceeding with InAppPayment::${inAppPayment.id} of type ${inAppPayment.type}...", true)

    check(inAppPayment.data.paymentMethodType.toPaymentSourceType() == PaymentSourceType.PayPal)

    store.update { InAppPaymentProcessorStage.PAYMENT_PIPELINE }

    disposables += SharedInAppPaymentPipeline.awaitTransaction(
      inAppPayment,
      PayPalPaymentSource(),
      requiredActionHandler
    ).subscribeOn(Schedulers.io()).subscribeBy(
      onComplete = {
        Log.d(TAG, "Finished ${inAppPayment.type} payment pipeline...", true)
        store.update { InAppPaymentProcessorStage.COMPLETE }
      },
      onError = {
        store.update { InAppPaymentProcessorStage.FAILED }
        SharedInAppPaymentPipeline.handleError(it, inAppPayment.id, PaymentSourceType.PayPal, inAppPayment.type.toErrorSource())
      }
    )
  }

  class Factory(
    private val payPalRepository: PayPalRepository = PayPalRepository(AppDependencies.donationsService)
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(PayPalPaymentInProgressViewModel(payPalRepository)) as T
    }
  }
}
