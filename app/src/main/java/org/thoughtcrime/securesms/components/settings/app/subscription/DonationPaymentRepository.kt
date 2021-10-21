package org.thoughtcrime.securesms.components.settings.app.subscription

import android.app.Activity
import android.content.Intent
import com.google.android.gms.wallet.PaymentData
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.money.FiatMoney
import org.signal.donations.GooglePayApi
import org.signal.donations.GooglePayPaymentSource
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.SubscriptionReceiptRequestResponseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.subscription.LevelUpdateOperation
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.util.Environment
import org.whispersystems.signalservice.api.subscriptions.IdempotencyKey
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
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

  private val googlePayApi = GooglePayApi(activity, StripeApi.Gateway(Environment.Donations.STRIPE_CONFIGURATION), Environment.Donations.GOOGLE_PAY_CONFIGURATION)
  private val stripeApi = StripeApi(Environment.Donations.STRIPE_CONFIGURATION, this, this, ApplicationDependencies.getOkHttpClient())

  fun isGooglePayAvailable(): Completable = googlePayApi.queryIsReadyToPay()

  fun requestTokenFromGooglePay(price: FiatMoney, label: String, requestCode: Int) {
    googlePayApi.requestPayment(price, label, requestCode)
  }

  fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?,
    expectedRequestCode: Int,
    paymentsRequestCallback: GooglePayApi.PaymentRequestCallback
  ) {
    googlePayApi.onActivityResult(requestCode, resultCode, data, expectedRequestCode, paymentsRequestCallback)
  }

  fun continuePayment(price: FiatMoney, paymentData: PaymentData): Completable {
    return stripeApi.createPaymentIntent(price)
      .flatMapCompletable { result ->
        when (result) {
          is StripeApi.CreatePaymentIntentResult.AmountIsTooSmall -> Completable.error(Exception("Amount is too small"))
          is StripeApi.CreatePaymentIntentResult.AmountIsTooLarge -> Completable.error(Exception("Amount is too large"))
          is StripeApi.CreatePaymentIntentResult.CurrencyIsNotSupported -> Completable.error(Exception("Currency is not supported"))
          is StripeApi.CreatePaymentIntentResult.Success -> stripeApi.confirmPaymentIntent(GooglePayPaymentSource(paymentData), result.paymentIntent)
        }
      }
  }

  fun continueSubscriptionSetup(paymentData: PaymentData): Completable {
    return stripeApi.createSetupIntent()
      .flatMapCompletable { result ->
        stripeApi.confirmSetupIntent(GooglePayPaymentSource(paymentData), result.setupIntent)
      }
  }

  fun cancelActiveSubscription(): Completable {
    val localSubscriber = SignalStore.donationsValues().requireSubscriber()
    return ApplicationDependencies.getDonationsService().cancelSubscription(localSubscriber.subscriberId).flatMapCompletable {
      when {
        it.status == 200 -> Completable.complete()
        it.applicationError.isPresent -> Completable.error(it.applicationError.get())
        it.executionError.isPresent -> Completable.error(it.executionError.get())
        else -> Completable.error(AssertionError("Something bad happened"))
      }
    }
  }

  fun ensureSubscriberId(): Completable {
    val subscriberId = SignalStore.donationsValues().getSubscriber()?.subscriberId ?: SubscriberId.generate()
    return ApplicationDependencies
      .getDonationsService()
      .putSubscription(subscriberId)
      .flatMapCompletable {
        when {
          it.status == 200 -> Completable.complete()
          it.applicationError.isPresent -> Completable.error(it.applicationError.get())
          it.executionError.isPresent -> Completable.error(it.executionError.get())
          else -> Completable.error(AssertionError("Something bad happened"))
        }
      }
      .doOnComplete {
        SignalStore
          .donationsValues()
          .setSubscriber(Subscriber(subscriberId, SignalStore.donationsValues().getSubscriptionCurrency().currencyCode))
      }
  }

  fun setSubscriptionLevel(subscriptionLevel: String): Completable {
    return getOrCreateLevelUpdateOperation(subscriptionLevel)
      .flatMapCompletable { levelUpdateOperation ->
        val subscriber = SignalStore.donationsValues().requireSubscriber()

        ApplicationDependencies.getDonationsService().updateSubscriptionLevel(
          subscriber.subscriberId,
          subscriptionLevel,
          subscriber.currencyCode,
          levelUpdateOperation.idempotencyKey.serialize()
        ).flatMapCompletable { response ->
          when {
            response.status == 200 -> Completable.complete()
            response.applicationError.isPresent -> Completable.error(response.applicationError.get())
            response.executionError.isPresent -> Completable.error(response.executionError.get())
            else -> Completable.error(AssertionError("should never happen"))
          }
        }.andThen {
          SignalStore.donationsValues().clearLevelOperation(levelUpdateOperation)
          it.onComplete()
        }.andThen {
          val jobIds = SubscriptionReceiptRequestResponseJob.enqueueSubscriptionContinuation()
          val countDownLatch = CountDownLatch(2)

          val firstJobListener = JobTracker.JobListener { _, jobState ->
            if (jobState.isComplete) {
              countDownLatch.countDown()
            }
          }

          val secondJobListener = JobTracker.JobListener { _, jobState ->
            if (jobState.isComplete) {
              countDownLatch.countDown()
            }
          }

          ApplicationDependencies.getJobManager().addListener(jobIds.first(), firstJobListener)
          ApplicationDependencies.getJobManager().addListener(jobIds.second(), secondJobListener)

          try {
            if (!countDownLatch.await(10, TimeUnit.SECONDS)) {
              it.onError(DonationExceptions.TimedOutWaitingForTokenRedemption)
            } else {
              it.onComplete()
            }
          } catch (e: InterruptedException) {
            it.onError(DonationExceptions.TimedOutWaitingForTokenRedemption)
          }
        }
      }.subscribeOn(Schedulers.io())
  }

  private fun getOrCreateLevelUpdateOperation(subscriptionLevel: String): Single<LevelUpdateOperation> = Single.fromCallable {
    val levelUpdateOperation = SignalStore.donationsValues().getLevelOperation()
    if (levelUpdateOperation == null || subscriptionLevel != levelUpdateOperation.level) {
      val newOperation = LevelUpdateOperation(
        idempotencyKey = IdempotencyKey.generate(),
        level = subscriptionLevel
      )

      SignalStore.donationsValues().setLevelOperation(newOperation)
      newOperation
    } else {
      levelUpdateOperation
    }
  }

  override fun fetchPaymentIntent(price: FiatMoney, description: String?): Single<StripeApi.PaymentIntent> {
    return ApplicationDependencies
      .getDonationsService()
      .createDonationIntentWithAmount(price.minimumUnitPrecisionString, price.currency.currencyCode)
      .flatMap { response ->
        when {
          response.status == 200 -> Single.just(StripeApi.PaymentIntent(response.result.get().id, response.result.get().clientSecret))
          response.executionError.isPresent -> Single.error(response.executionError.get())
          response.applicationError.isPresent -> Single.error(response.applicationError.get())
          else -> Single.error(AssertionError("should never get here"))
        }
      }
  }

  override fun fetchSetupIntent(): Single<StripeApi.SetupIntent> {
    return Single.fromCallable {
      SignalStore.donationsValues().requireSubscriber()
    }.flatMap {
      ApplicationDependencies.getDonationsService().createSubscriptionPaymentMethod(it.subscriberId)
    }.flatMap { response ->
      when {
        response.status == 200 -> Single.just(StripeApi.SetupIntent(response.result.get().id, response.result.get().clientSecret))
        response.executionError.isPresent -> Single.error(response.executionError.get())
        response.applicationError.isPresent -> Single.error(response.applicationError.get())
        else -> Single.error(AssertionError("should never get here"))
      }
    }
  }

  override fun setDefaultPaymentMethod(paymentMethodId: String): Completable {
    return Single.fromCallable {
      SignalStore.donationsValues().requireSubscriber()
    }.flatMap {
      ApplicationDependencies.getDonationsService().setDefaultPaymentMethodId(it.subscriberId, paymentMethodId)
    }.flatMapCompletable { response ->
      when {
        response.status == 200 -> Completable.complete()
        response.executionError.isPresent -> Completable.error(response.executionError.get())
        response.applicationError.isPresent -> Completable.error(response.applicationError.get())
        else -> Completable.error(AssertionError("Should never get here"))
      }
    }
  }
}
