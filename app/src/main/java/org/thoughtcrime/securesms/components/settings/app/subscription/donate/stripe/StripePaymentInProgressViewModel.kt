package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import androidx.lifecycle.ViewModel
import com.google.android.gms.wallet.PaymentData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.donations.GooglePayPaymentSource
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSource
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorStage
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.RequiredActionHandler
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.SharedInAppPaymentPipeline
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.util.Preconditions

class StripePaymentInProgressViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(StripePaymentInProgressViewModel::class.java)
  }

  private val store = RxStore(InAppPaymentProcessorStage.INIT)
  val state: Flowable<InAppPaymentProcessorStage> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  private val disposables = CompositeDisposable()
  private var stripePaymentData: StripePaymentData? = null

  override fun onCleared() {
    disposables.clear()
    store.dispose()
    clearPaymentInformation()
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

  fun processNewDonation(inAppPaymentId: InAppPaymentTable.InAppPaymentId, oneTimeRequiredActionHandler: RequiredActionHandler, monthlyRequiredActionHandler: RequiredActionHandler) {
    store.update { InAppPaymentProcessorStage.PAYMENT_PIPELINE }
    val iap = InAppPaymentsRepository.requireInAppPayment(inAppPaymentId)

    disposables += iap.flatMap { inAppPayment ->
      resolvePaymentSourceProvider(inAppPayment.type.toErrorSource()).paymentSource.flatMap { paymentSource ->
        SharedInAppPaymentPipeline.awaitTransaction(
          inAppPaymentId,
          paymentSource,
          oneTimeRequiredActionHandler,
          monthlyRequiredActionHandler
        )
      }
    }.subscribeOn(Schedulers.io()).subscribeBy(
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

  private fun resolvePaymentSourceProvider(errorSource: DonationErrorSource): PaymentSourceProvider {
    return when (val data = stripePaymentData) {
      is StripePaymentData.GooglePay -> PaymentSourceProvider(
        PaymentSourceType.Stripe.GooglePay,
        Single.just<PaymentSource>(GooglePayPaymentSource(data.paymentData)).doAfterTerminate { clearPaymentInformation() }
      )

      is StripePaymentData.CreditCard -> PaymentSourceProvider(
        PaymentSourceType.Stripe.CreditCard,
        StripeRepository.createCreditCardPaymentSource(errorSource, data.cardData).doAfterTerminate { clearPaymentInformation() }
      )

      is StripePaymentData.SEPADebit -> PaymentSourceProvider(
        PaymentSourceType.Stripe.SEPADebit,
        StripeRepository.createSEPADebitPaymentSource(data.sepaDebitData).doAfterTerminate { clearPaymentInformation() }
      )

      is StripePaymentData.IDEAL -> PaymentSourceProvider(
        PaymentSourceType.Stripe.IDEAL,
        StripeRepository.createIdealPaymentSource(data.idealData).doAfterTerminate { clearPaymentInformation() }
      )

      else -> error("This should never happen.")
    }
  }

  fun providePaymentData(paymentData: PaymentData) {
    requireNoPaymentInformation()
    this.stripePaymentData = StripePaymentData.GooglePay(paymentData)
  }

  fun provideCardData(cardData: StripeApi.CardData) {
    requireNoPaymentInformation()
    this.stripePaymentData = StripePaymentData.CreditCard(cardData)
  }

  fun provideSEPADebitData(bankData: StripeApi.SEPADebitData) {
    requireNoPaymentInformation()
    this.stripePaymentData = StripePaymentData.SEPADebit(bankData)
  }

  fun provideIDEALData(bankData: StripeApi.IDEALData) {
    requireNoPaymentInformation()
    this.stripePaymentData = StripePaymentData.IDEAL(bankData)
  }

  fun getInAppPaymentType(inAppPaymentId: InAppPaymentTable.InAppPaymentId): Single<InAppPaymentType> {
    return InAppPaymentsRepository.requireInAppPayment(inAppPaymentId).map { it.type }.observeOn(AndroidSchedulers.mainThread())
  }

  private fun requireNoPaymentInformation() {
    require(stripePaymentData == null)
  }

  private fun clearPaymentInformation() {
    Log.d(TAG, "Cleared payment information.", true)
    stripePaymentData = null
  }

  fun cancelSubscription(subscriberType: InAppPaymentSubscriberRecord.Type) {
    Log.d(TAG, "Beginning cancellation...", true)

    store.update { InAppPaymentProcessorStage.CANCELLING }
    disposables += RecurringInAppPaymentRepository.cancelActiveSubscription(subscriberType).subscribeBy(
      onComplete = {
        Log.d(TAG, "Cancellation succeeded", true)
        store.update { InAppPaymentProcessorStage.COMPLETE }
      },
      onError = { throwable ->
        Log.w(TAG, "Cancellation failed", throwable, true)
        store.update { InAppPaymentProcessorStage.FAILED }
      }
    )
  }

  fun updateSubscription(inAppPaymentId: InAppPaymentTable.InAppPaymentId) {
    Log.d(TAG, "Beginning subscription update...", true)
    store.update { InAppPaymentProcessorStage.PAYMENT_PIPELINE }
    val iap = InAppPaymentsRepository.requireInAppPayment(inAppPaymentId)
    disposables += iap.flatMap { inAppPayment ->
      RecurringInAppPaymentRepository
        .cancelActiveSubscriptionIfNecessary(inAppPayment.type.requireSubscriberType())
        .andThen(RecurringInAppPaymentRepository.getPaymentSourceTypeOfLatestSubscription(inAppPayment.type.requireSubscriberType()))
        .flatMap { paymentSourceType ->
          val freshPayment = SignalDatabase.inAppPayments.moveToTransacting(inAppPayment.id)!!

          Single.fromCallable {
            RecurringInAppPaymentRepository.setSubscriptionLevelSync(freshPayment)
          }.flatMap { SharedInAppPaymentPipeline.awaitRedemption(it, paymentSourceType) }
        }
    }
      .subscribeOn(Schedulers.io())
      .subscribeBy(
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

  private data class PaymentSourceProvider(
    val paymentSourceType: PaymentSourceType,
    val paymentSource: Single<PaymentSource>
  )

  private sealed interface StripePaymentData {
    class GooglePay(val paymentData: PaymentData) : StripePaymentData
    class CreditCard(val cardData: StripeApi.CardData) : StripePaymentData
    class SEPADebit(val sepaDebitData: StripeApi.SEPADebitData) : StripePaymentData
    class IDEAL(val idealData: StripeApi.IDEALData) : StripePaymentData
  }
}
