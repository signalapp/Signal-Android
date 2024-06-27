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
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.donations.GooglePayPaymentSource
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeApi
import org.signal.donations.StripeIntentAccessor
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.OneTimeInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorStage
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.toDonationError
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.util.Preconditions
import org.whispersystems.signalservice.internal.push.exceptions.DonationProcessorError

class StripePaymentInProgressViewModel(
  private val stripeRepository: StripeRepository,
  private val oneTimeInAppPaymentRepository: OneTimeInAppPaymentRepository
) : ViewModel() {

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

  fun processNewDonation(inAppPayment: InAppPaymentTable.InAppPayment, nextActionHandler: StripeNextActionHandler) {
    Log.d(TAG, "Proceeding with InAppPayment::${inAppPayment.id} of type ${inAppPayment.type}...", true)

    val paymentSourceProvider: PaymentSourceProvider = resolvePaymentSourceProvider(inAppPayment.type.toErrorSource())

    return if (inAppPayment.type.recurring) {
      proceedMonthly(inAppPayment, paymentSourceProvider, nextActionHandler)
    } else {
      proceedOneTime(inAppPayment, paymentSourceProvider, nextActionHandler)
    }
  }

  private fun resolvePaymentSourceProvider(errorSource: DonationErrorSource): PaymentSourceProvider {
    return when (val data = stripePaymentData) {
      is StripePaymentData.GooglePay -> PaymentSourceProvider(
        PaymentSourceType.Stripe.GooglePay,
        Single.just<StripeApi.PaymentSource>(GooglePayPaymentSource(data.paymentData)).doAfterTerminate { clearPaymentInformation() }
      )

      is StripePaymentData.CreditCard -> PaymentSourceProvider(
        PaymentSourceType.Stripe.CreditCard,
        stripeRepository.createCreditCardPaymentSource(errorSource, data.cardData).doAfterTerminate { clearPaymentInformation() }
      )

      is StripePaymentData.SEPADebit -> PaymentSourceProvider(
        PaymentSourceType.Stripe.SEPADebit,
        stripeRepository.createSEPADebitPaymentSource(data.sepaDebitData).doAfterTerminate { clearPaymentInformation() }
      )

      is StripePaymentData.IDEAL -> PaymentSourceProvider(
        PaymentSourceType.Stripe.IDEAL,
        stripeRepository.createIdealPaymentSource(data.idealData).doAfterTerminate { clearPaymentInformation() }
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

  private fun requireNoPaymentInformation() {
    require(stripePaymentData == null)
  }

  private fun clearPaymentInformation() {
    Log.d(TAG, "Cleared payment information.", true)
    stripePaymentData = null
  }

  private fun proceedMonthly(inAppPayment: InAppPaymentTable.InAppPayment, paymentSourceProvider: PaymentSourceProvider, nextActionHandler: StripeNextActionHandler) {
    val ensureSubscriberId: Completable = RecurringInAppPaymentRepository.ensureSubscriberId(inAppPayment.type.requireSubscriberType())
    val createAndConfirmSetupIntent: Single<StripeApi.Secure3DSAction> = paymentSourceProvider.paymentSource.flatMap {
      stripeRepository.createAndConfirmSetupIntent(inAppPayment.type, it, paymentSourceProvider.paymentSourceType as PaymentSourceType.Stripe)
    }

    val setLevel: Completable = RecurringInAppPaymentRepository.setSubscriptionLevel(inAppPayment, paymentSourceProvider.paymentSourceType)

    Log.d(TAG, "Starting subscription payment pipeline...", true)
    store.update { InAppPaymentProcessorStage.PAYMENT_PIPELINE }

    val setup: Completable = ensureSubscriberId
      .andThen(RecurringInAppPaymentRepository.cancelActiveSubscriptionIfNecessary(inAppPayment.type.requireSubscriberType()))
      .andThen(createAndConfirmSetupIntent)
      .flatMap { secure3DSAction ->
        nextActionHandler.handle(
          action = secure3DSAction,
          inAppPayment = inAppPayment.copy(
            data = inAppPayment.data.copy(
              redemption = null,
              waitForAuth = InAppPaymentData.WaitingForAuthorizationState(
                stripeIntentId = secure3DSAction.stripeIntentAccessor.intentId,
                stripeClientSecret = secure3DSAction.stripeIntentAccessor.intentClientSecret
              )
            )
          )
        )
          .flatMap { secure3DSResult -> stripeRepository.getStatusAndPaymentMethodId(secure3DSResult, secure3DSAction.paymentMethodId) }
      }
      .flatMapCompletable { stripeRepository.setDefaultPaymentMethod(it.paymentMethod!!, it.intentId, inAppPayment.type.requireSubscriberType(), paymentSourceProvider.paymentSourceType) }
      .onErrorResumeNext {
        when (it) {
          is DonationError -> Completable.error(it)
          is DonationProcessorError -> Completable.error(it.toDonationError(DonationErrorSource.MONTHLY, paymentSourceProvider.paymentSourceType))
          else -> Completable.error(DonationError.getPaymentSetupError(DonationErrorSource.MONTHLY, it, paymentSourceProvider.paymentSourceType))
        }
      }

    disposables += setup.andThen(setLevel).subscribeBy(
      onError = { throwable ->
        Log.w(TAG, "Failure in subscription payment pipeline...", throwable, true)
        store.update { InAppPaymentProcessorStage.FAILED }
        InAppPaymentsRepository.handlePipelineError(inAppPayment.id, DonationErrorSource.MONTHLY, paymentSourceProvider.paymentSourceType, throwable)
      },
      onComplete = {
        Log.d(TAG, "Finished subscription payment pipeline...", true)
        store.update { InAppPaymentProcessorStage.COMPLETE }
      }
    )
  }

  private fun proceedOneTime(
    inAppPayment: InAppPaymentTable.InAppPayment,
    paymentSourceProvider: PaymentSourceProvider,
    nextActionHandler: StripeNextActionHandler
  ) {
    Log.w(TAG, "Beginning one-time payment pipeline...", true)

    val amount = inAppPayment.data.amount!!.toFiatMoney()
    val recipientId = inAppPayment.data.recipientId?.let { RecipientId.from(it) } ?: Recipient.self().id
    val verifyUser = if (inAppPayment.type == InAppPaymentType.ONE_TIME_GIFT) {
      OneTimeInAppPaymentRepository.verifyRecipientIsAllowedToReceiveAGift(recipientId)
    } else {
      Completable.complete()
    }

    val continuePayment: Single<StripeIntentAccessor> = verifyUser.andThen(stripeRepository.continuePayment(amount, recipientId, inAppPayment.data.level, paymentSourceProvider.paymentSourceType))
    val intentAndSource: Single<Pair<StripeIntentAccessor, StripeApi.PaymentSource>> = Single.zip(continuePayment, paymentSourceProvider.paymentSource, ::Pair)

    disposables += intentAndSource.flatMapCompletable { (paymentIntent, paymentSource) ->
      stripeRepository.confirmPayment(paymentSource, paymentIntent, recipientId)
        .flatMap { action ->
          nextActionHandler
            .handle(
              action = action,
              inAppPayment = inAppPayment.copy(
                state = InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION,
                data = inAppPayment.data.copy(
                  redemption = null,
                  waitForAuth = InAppPaymentData.WaitingForAuthorizationState(
                    stripeIntentId = action.stripeIntentAccessor.intentId,
                    stripeClientSecret = action.stripeIntentAccessor.intentClientSecret
                  )
                )
              )
            )
            .flatMap { stripeRepository.getStatusAndPaymentMethodId(it, action.paymentMethodId) }
        }
        .flatMapCompletable {
          oneTimeInAppPaymentRepository.waitForOneTimeRedemption(
            inAppPayment = inAppPayment,
            paymentIntentId = paymentIntent.intentId,
            paymentSourceType = paymentSource.type
          )
        }
    }.subscribeBy(
      onError = { throwable ->
        Log.w(TAG, "Failure in one-time payment pipeline...", throwable, true)
        store.update { InAppPaymentProcessorStage.FAILED }
        InAppPaymentsRepository.handlePipelineError(inAppPayment.id, DonationErrorSource.ONE_TIME, paymentSourceProvider.paymentSourceType, throwable)
      },
      onComplete = {
        Log.w(TAG, "Completed one-time payment pipeline...", true)
        store.update { InAppPaymentProcessorStage.COMPLETE }
      }
    )
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

  fun updateSubscription(inAppPayment: InAppPaymentTable.InAppPayment) {
    Log.d(TAG, "Beginning subscription update...", true)
    store.update { InAppPaymentProcessorStage.PAYMENT_PIPELINE }
    disposables += RecurringInAppPaymentRepository
      .cancelActiveSubscriptionIfNecessary(inAppPayment.type.requireSubscriberType())
      .andThen(RecurringInAppPaymentRepository.getPaymentSourceTypeOfLatestSubscription(inAppPayment.type.requireSubscriberType()))
      .flatMapCompletable { paymentSourceType -> RecurringInAppPaymentRepository.setSubscriptionLevel(inAppPayment, paymentSourceType) }
      .subscribeBy(
        onComplete = {
          Log.w(TAG, "Completed subscription update", true)
          store.update { InAppPaymentProcessorStage.COMPLETE }
        },
        onError = { throwable ->
          Log.w(TAG, "Failed to update subscription", throwable, true)
          store.update { InAppPaymentProcessorStage.FAILED }
          SignalExecutors.BOUNDED_IO.execute {
            val paymentSourceType = InAppPaymentsRepository.getLatestPaymentMethodType(inAppPayment.type.requireSubscriberType()).toPaymentSourceType()
            InAppPaymentsRepository.handlePipelineError(inAppPayment.id, DonationErrorSource.MONTHLY, paymentSourceType, throwable)
          }
        }
      )
  }

  private data class PaymentSourceProvider(
    val paymentSourceType: PaymentSourceType,
    val paymentSource: Single<StripeApi.PaymentSource>
  )

  private sealed interface StripePaymentData {
    class GooglePay(val paymentData: PaymentData) : StripePaymentData
    class CreditCard(val cardData: StripeApi.CardData) : StripePaymentData
    class SEPADebit(val sepaDebitData: StripeApi.SEPADebitData) : StripePaymentData
    class IDEAL(val idealData: StripeApi.IDEALData) : StripePaymentData
  }

  class Factory(
    private val stripeRepository: StripeRepository,
    private val oneTimeInAppPaymentRepository: OneTimeInAppPaymentRepository = OneTimeInAppPaymentRepository(AppDependencies.donationsService)
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StripePaymentInProgressViewModel(stripeRepository, oneTimeInAppPaymentRepository)) as T
    }
  }
}
