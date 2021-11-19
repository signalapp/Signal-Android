package org.thoughtcrime.securesms.components.settings.app.subscription

import android.app.Activity
import android.content.Intent
import com.google.android.gms.wallet.PaymentData
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.GooglePayApi
import org.signal.donations.GooglePayPaymentSource
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.BoostReceiptRequestResponseJob
import org.thoughtcrime.securesms.jobs.SubscriptionReceiptRequestResponseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.subscription.LevelUpdateOperation
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.util.Environment
import org.whispersystems.signalservice.api.subscriptions.IdempotencyKey
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.api.subscriptions.SubscriptionClientSecret
import org.whispersystems.signalservice.internal.EmptyResponse
import org.whispersystems.signalservice.internal.ServiceResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Manages bindings with payment APIs
 *
 * Steps for setting up payments for a subscription:
 * 1. Ask GooglePay for a payment token. This will pop up the Google Pay Sheet, which allows the user to select a payment method.
 * 1. Generate and send a SubscriberId, which is a 32 byte ID representing this user, to Signal Service, which creates a Stripe Customer
 * 1. Create a SetupIntent via the Stripe API
 * 1. Create a PaymentMethod vai the Stripe API, utilizing the token from Google Pay
 * 1. Confirm the SetupIntent via the Stripe API
 * 1. Set the default PaymentMethod for the customer, using the PaymentMethod id, via the Signal service
 *
 * For Boosts:
 * 1. Ask GooglePay for a payment token. This will pop up the Google Pay Sheet, which allows the user to select a payment method.
 * 1. Create a PaymentIntent via the Stripe API
 * 1. Create a PaymentMethod vai the Stripe API, utilizing the token from Google Pay
 * 1. Confirm the PaymentIntent via the Stripe API
 */
class DonationPaymentRepository(activity: Activity) : StripeApi.PaymentIntentFetcher, StripeApi.SetupIntentHelper {

  private val application = activity.application
  private val googlePayApi = GooglePayApi(activity, StripeApi.Gateway(Environment.Donations.STRIPE_CONFIGURATION), Environment.Donations.GOOGLE_PAY_CONFIGURATION)
  private val stripeApi = StripeApi(Environment.Donations.STRIPE_CONFIGURATION, this, this, ApplicationDependencies.getOkHttpClient())

  fun isGooglePayAvailable(): Completable = googlePayApi.queryIsReadyToPay()

  fun scheduleSyncForAccountRecordChange() {
    SignalExecutors.BOUNDED.execute {
      scheduleSyncForAccountRecordChangeSync()
    }
  }

  private fun scheduleSyncForAccountRecordChangeSync() {
    SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun requestTokenFromGooglePay(price: FiatMoney, label: String, requestCode: Int) {
    Log.d(TAG, "Requesting a token from google pay...")
    googlePayApi.requestPayment(price, label, requestCode)
  }

  fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?,
    expectedRequestCode: Int,
    paymentsRequestCallback: GooglePayApi.PaymentRequestCallback
  ) {
    Log.d(TAG, "Processing possible google pay result...")
    googlePayApi.onActivityResult(requestCode, resultCode, data, expectedRequestCode, paymentsRequestCallback)
  }

  fun continuePayment(price: FiatMoney, paymentData: PaymentData): Completable {
    Log.d(TAG, "Creating payment intent...", true)
    return stripeApi.createPaymentIntent(price, application.getString(R.string.Boost__thank_you_for_your_donation))
      .onErrorResumeNext { Single.error(DonationExceptions.SetupFailed(it)) }
      .flatMapCompletable { result ->
        Log.d(TAG, "Created payment intent.", true)
        when (result) {
          is StripeApi.CreatePaymentIntentResult.AmountIsTooSmall -> Completable.error(DonationExceptions.SetupFailed(Exception("Boost amount is too small")))
          is StripeApi.CreatePaymentIntentResult.AmountIsTooLarge -> Completable.error(DonationExceptions.SetupFailed(Exception("Boost amount is too large")))
          is StripeApi.CreatePaymentIntentResult.CurrencyIsNotSupported -> Completable.error(DonationExceptions.SetupFailed(Exception("Boost currency is not supported")))
          is StripeApi.CreatePaymentIntentResult.Success -> confirmPayment(paymentData, result.paymentIntent)
        }
      }
  }

  fun continueSubscriptionSetup(paymentData: PaymentData): Completable {
    Log.d(TAG, "Continuing subscription setup...", true)
    return stripeApi.createSetupIntent()
      .flatMapCompletable { result ->
        Log.d(TAG, "Retrieved SetupIntent, confirming...", true)
        stripeApi.confirmSetupIntent(GooglePayPaymentSource(paymentData), result.setupIntent).doOnComplete {
          Log.d(TAG, "Confirmed SetupIntent...", true)
        }
      }
  }

  fun cancelActiveSubscription(): Completable {
    Log.d(TAG, "Canceling active subscription...", true)
    val localSubscriber = SignalStore.donationsValues().requireSubscriber()
    return ApplicationDependencies.getDonationsService()
      .cancelSubscription(localSubscriber.subscriberId)
      .flatMap(ServiceResponse<EmptyResponse>::flattenResult)
      .ignoreElement()
      .doOnComplete { Log.d(TAG, "Cancelled active subscription.", true) }
  }

  fun ensureSubscriberId(): Completable {
    Log.d(TAG, "Ensuring SubscriberId exists on Signal service...", true)
    val subscriberId = SignalStore.donationsValues().getSubscriber()?.subscriberId ?: SubscriberId.generate()
    return ApplicationDependencies
      .getDonationsService()
      .putSubscription(subscriberId)
      .flatMap(ServiceResponse<EmptyResponse>::flattenResult).ignoreElement()
      .doOnComplete {
        Log.d(TAG, "Successfully set SubscriberId exists on Signal service.", true)

        SignalStore
          .donationsValues()
          .setSubscriber(Subscriber(subscriberId, SignalStore.donationsValues().getSubscriptionCurrency().currencyCode))

        scheduleSyncForAccountRecordChangeSync()
      }
  }

  private fun confirmPayment(paymentData: PaymentData, paymentIntent: StripeApi.PaymentIntent): Completable {
    Log.d(TAG, "Confirming payment intent...", true)
    val confirmPayment = stripeApi.confirmPaymentIntent(GooglePayPaymentSource(paymentData), paymentIntent)
    val waitOnRedemption = Completable.create {
      Log.d(TAG, "Confirmed payment intent.", true)

      val countDownLatch = CountDownLatch(1)
      var finalJobState: JobTracker.JobState? = null

      BoostReceiptRequestResponseJob.createJobChain(paymentIntent).enqueue { _, jobState ->
        if (jobState.isComplete) {
          finalJobState = jobState
          countDownLatch.countDown()
        }
      }

      try {
        if (countDownLatch.await(10, TimeUnit.SECONDS)) {
          when (finalJobState) {
            JobTracker.JobState.SUCCESS -> {
              Log.d(TAG, "Boost request response job chain succeeded.", true)
              it.onComplete()
            }
            JobTracker.JobState.FAILURE -> {
              Log.d(TAG, "Boost request response job chain failed permanently.", true)
              it.onError(DonationExceptions.RedemptionFailed)
            }
            else -> {
              Log.d(TAG, "Boost request response job chain ignored due to in-progress jobs.", true)
              it.onError(DonationExceptions.TimedOutWaitingForTokenRedemption)
            }
          }
        } else {
          Log.d(TAG, "Boost redemption timed out waiting for job completion.", true)
          it.onError(DonationExceptions.TimedOutWaitingForTokenRedemption)
        }
      } catch (e: InterruptedException) {
        Log.d(TAG, "Boost redemption job interrupted", e, true)
        it.onError(DonationExceptions.TimedOutWaitingForTokenRedemption)
      }
    }

    return confirmPayment.andThen(waitOnRedemption)
  }

  fun setSubscriptionLevel(subscriptionLevel: String): Completable {
    return getOrCreateLevelUpdateOperation(subscriptionLevel)
      .flatMapCompletable { levelUpdateOperation ->
        val subscriber = SignalStore.donationsValues().requireSubscriber()

        Log.d(TAG, "Attempting to set user subscription level to $subscriptionLevel", true)
        ApplicationDependencies.getDonationsService().updateSubscriptionLevel(
          subscriber.subscriberId,
          subscriptionLevel,
          subscriber.currencyCode,
          levelUpdateOperation.idempotencyKey.serialize(),
          SubscriptionReceiptRequestResponseJob.MUTEX
        ).flatMapCompletable {
          if (it.status == 200 || it.status == 204) {
            Log.d(TAG, "Successfully set user subscription to level $subscriptionLevel with response code ${it.status}", true)
            SignalStore.donationsValues().clearUserManuallyCancelled()
            SignalStore.donationsValues().clearLevelOperations()
            LevelUpdate.updateProcessingState(false)
            Completable.complete()
          } else {
            if (it.applicationError.isPresent) {
              Log.w(TAG, "Failed to set user subscription to level $subscriptionLevel with response code ${it.status}", it.applicationError.get(), true)
              SignalStore.donationsValues().clearLevelOperations()
            } else {
              Log.w(TAG, "Failed to set user subscription to level $subscriptionLevel", it.executionError.orNull(), true)
            }

            LevelUpdate.updateProcessingState(false)
            it.flattenResult().ignoreElement()
          }
        }.andThen {
          Log.d(TAG, "Enqueuing request response job chain.", true)
          val countDownLatch = CountDownLatch(1)
          var finalJobState: JobTracker.JobState? = null

          SubscriptionReceiptRequestResponseJob.createSubscriptionContinuationJobChain().enqueue { _, jobState ->
            if (jobState.isComplete) {
              finalJobState = jobState
              countDownLatch.countDown()
            }
          }

          try {
            if (countDownLatch.await(10, TimeUnit.SECONDS)) {
              when (finalJobState) {
                JobTracker.JobState.SUCCESS -> {
                  Log.d(TAG, "Subscription request response job chain succeeded.", true)
                  it.onComplete()
                }
                JobTracker.JobState.FAILURE -> {
                  Log.d(TAG, "Subscription request response job chain failed permanently.", true)
                  it.onError(DonationExceptions.RedemptionFailed)
                }
                else -> {
                  Log.d(TAG, "Subscription request response job chain ignored due to in-progress jobs.", true)
                  it.onError(DonationExceptions.TimedOutWaitingForTokenRedemption)
                }
              }
            } else {
              Log.d(TAG, "Subscription request response job timed out.", true)
              it.onError(DonationExceptions.TimedOutWaitingForTokenRedemption)
            }
          } catch (e: InterruptedException) {
            Log.w(TAG, "Subscription request response interrupted.", e, true)
            it.onError(DonationExceptions.TimedOutWaitingForTokenRedemption)
          }
        }
      }.doOnError {
        LevelUpdate.updateProcessingState(false)
      }.subscribeOn(Schedulers.io())
  }

  private fun getOrCreateLevelUpdateOperation(subscriptionLevel: String): Single<LevelUpdateOperation> = Single.fromCallable {
    Log.d(TAG, "Retrieving level update operation for $subscriptionLevel")
    val levelUpdateOperation = SignalStore.donationsValues().getLevelOperation(subscriptionLevel)
    if (levelUpdateOperation == null) {
      val newOperation = LevelUpdateOperation(
        idempotencyKey = IdempotencyKey.generate(),
        level = subscriptionLevel
      )

      SignalStore.donationsValues().setLevelOperation(newOperation)
      LevelUpdate.updateProcessingState(true)
      Log.d(TAG, "Created a new operation for $subscriptionLevel")
      newOperation
    } else {
      LevelUpdate.updateProcessingState(true)
      Log.d(TAG, "Reusing operation for $subscriptionLevel")
      levelUpdateOperation
    }
  }

  override fun fetchPaymentIntent(price: FiatMoney, description: String?): Single<StripeApi.PaymentIntent> {
    Log.d(TAG, "Fetching payment intent from Signal service...")
    return ApplicationDependencies
      .getDonationsService()
      .createDonationIntentWithAmount(price.minimumUnitPrecisionString, price.currency.currencyCode, description)
      .flatMap(ServiceResponse<SubscriptionClientSecret>::flattenResult)
      .map {
        StripeApi.PaymentIntent(it.id, it.clientSecret)
      }.doOnSuccess {
        Log.d(TAG, "Got payment intent from Signal service!")
      }
  }

  override fun fetchSetupIntent(): Single<StripeApi.SetupIntent> {
    Log.d(TAG, "Fetching setup intent from Signal service...")
    return Single.fromCallable { SignalStore.donationsValues().requireSubscriber() }
      .flatMap { ApplicationDependencies.getDonationsService().createSubscriptionPaymentMethod(it.subscriberId) }
      .flatMap(ServiceResponse<SubscriptionClientSecret>::flattenResult)
      .map { StripeApi.SetupIntent(it.id, it.clientSecret) }
      .doOnSuccess {
        Log.d(TAG, "Got setup intent from Signal service!")
      }
  }

  override fun setDefaultPaymentMethod(paymentMethodId: String): Completable {
    Log.d(TAG, "Setting default payment method via Signal service...")
    return Single.fromCallable {
      SignalStore.donationsValues().requireSubscriber()
    }.flatMap {
      ApplicationDependencies.getDonationsService().setDefaultPaymentMethodId(it.subscriberId, paymentMethodId)
    }.flatMap(ServiceResponse<EmptyResponse>::flattenResult).ignoreElement().doOnComplete {
      Log.d(TAG, "Set default payment method via Signal service!")
    }
  }

  companion object {
    private val TAG = Log.tag(DonationPaymentRepository::class.java)
  }
}
