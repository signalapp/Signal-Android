package org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleSource
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.donations.PayPalPaymentSource
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorStage
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.RequiredActionHandler
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.SharedInAppPaymentPipeline
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.jobs.MultiDeviceSubscriptionSyncRequestJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.util.Preconditions

class PayPalPaymentInProgressViewModel : ViewModel() {

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

  fun getInAppPaymentType(inAppPaymentId: InAppPaymentTable.InAppPaymentId): Single<InAppPaymentType> {
    return InAppPaymentsRepository.requireInAppPayment(inAppPaymentId).map { it.type }.observeOn(AndroidSchedulers.mainThread())
  }

  fun updateSubscription(inAppPaymentId: InAppPaymentTable.InAppPaymentId) {
    Log.d(TAG, "Beginning subscription update...", true)

    store.update { InAppPaymentProcessorStage.PAYMENT_PIPELINE }
    val iap = InAppPaymentsRepository.requireInAppPayment(inAppPaymentId)

    disposables += iap.flatMap { inAppPayment ->
      RecurringInAppPaymentRepository.cancelActiveSubscriptionIfNecessary(inAppPayment.type.requireSubscriberType()).andThen(
        SingleSource<InAppPaymentTable.InAppPayment> {
          val freshPayment = SignalDatabase.inAppPayments.moveToTransacting(inAppPayment.id)!!
          RecurringInAppPaymentRepository.setSubscriptionLevelSync(freshPayment)
        }
      ).flatMap {
        SharedInAppPaymentPipeline.awaitRedemption(it, PaymentSourceType.PayPal)
      }
    }.subscribeBy(
      onSuccess = {
        Log.w(TAG, "Completed subscription update", true)
        store.update { InAppPaymentProcessorStage.COMPLETE }
      },
      onError = { throwable ->
        Log.w(TAG, "Failed to update subscription", throwable, true)
        store.update { InAppPaymentProcessorStage.FAILED }
        SignalExecutors.BOUNDED_IO.execute {
          InAppPaymentsRepository.handlePipelineError(inAppPaymentId, throwable)
        }
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

  fun processNewDonation(
    inAppPaymentId: InAppPaymentTable.InAppPaymentId,
    oneTimeActionHandler: RequiredActionHandler,
    monthlyActionHandler: RequiredActionHandler
  ) {
    store.update { InAppPaymentProcessorStage.PAYMENT_PIPELINE }

    disposables += SharedInAppPaymentPipeline.awaitTransaction(
      inAppPaymentId,
      PayPalPaymentSource(),
      oneTimeActionHandler,
      monthlyActionHandler
    ).subscribeOn(Schedulers.io()).subscribeBy(
      onSuccess = {
        Log.d(TAG, "Finished ${it.type} payment pipeline...", true)
        store.update { InAppPaymentProcessorStage.COMPLETE }
      },
      onError = {
        store.update { InAppPaymentProcessorStage.FAILED }
        SignalExecutors.BOUNDED_IO.execute {
          InAppPaymentsRepository.handlePipelineError(inAppPaymentId, it)
        }
      }
    )
  }
}
