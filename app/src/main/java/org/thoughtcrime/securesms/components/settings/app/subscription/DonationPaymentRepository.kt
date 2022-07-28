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
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.BoostReceiptRequestResponseJob
import org.thoughtcrime.securesms.jobs.SubscriptionReceiptRequestResponseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.subscription.LevelUpdateOperation
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.ProfileUtil
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.subscriptions.IdempotencyKey
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.api.subscriptions.SubscriptionClientSecret
import org.whispersystems.signalservice.internal.EmptyResponse
import org.whispersystems.signalservice.internal.ServiceResponse
import java.io.IOException
import java.util.Locale
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
 * For Boosts and Gifts:
 * 1. Ask GooglePay for a payment token. This will pop up the Google Pay Sheet, which allows the user to select a payment method.
 * 1. Create a PaymentIntent via the Stripe API
 * 1. Create a PaymentMethod vai the Stripe API, utilizing the token from Google Pay
 * 1. Confirm the PaymentIntent via the Stripe API
 */
class DonationPaymentRepository(activity: Activity) : StripeApi.PaymentIntentFetcher, StripeApi.SetupIntentHelper {

  private val googlePayApi = GooglePayApi(activity, StripeApi.Gateway(Environment.Donations.STRIPE_CONFIGURATION), Environment.Donations.GOOGLE_PAY_CONFIGURATION)
  private val stripeApi = StripeApi(Environment.Donations.STRIPE_CONFIGURATION, this, this, ApplicationDependencies.getOkHttpClient())

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

  /**
   * Verifies that the given recipient is a supported target for a gift.
   */
  fun verifyRecipientIsAllowedToReceiveAGift(badgeRecipient: RecipientId): Completable {
    return Completable.fromAction {
      Log.d(TAG, "Verifying badge recipient $badgeRecipient", true)
      val recipient = Recipient.resolved(badgeRecipient)

      if (recipient.isSelf) {
        Log.d(TAG, "Cannot send a gift to self.", true)
        throw DonationError.GiftRecipientVerificationError.SelectedRecipientDoesNotSupportGifts
      }

      if (recipient.isGroup || recipient.isDistributionList || recipient.registered != RecipientDatabase.RegisteredState.REGISTERED) {
        Log.w(TAG, "Invalid badge recipient $badgeRecipient. Verification failed.", true)
        throw DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid
      }

      try {
        val profile = ProfileUtil.retrieveProfileSync(ApplicationDependencies.getApplication(), recipient, SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL)
        if (!profile.profile.capabilities.isGiftBadges) {
          Log.w(TAG, "Badge recipient does not support gifting. Verification failed.", true)
          throw DonationError.GiftRecipientVerificationError.SelectedRecipientDoesNotSupportGifts
        } else {
          Log.d(TAG, "Badge recipient supports gifting. Verification successful.", true)
        }
      } catch (e: IOException) {
        Log.w(TAG, "Failed to retrieve profile for recipient.", e, true)
        throw DonationError.GiftRecipientVerificationError.FailedToFetchProfile(e)
      }
    }.subscribeOn(Schedulers.io())
  }

  /**
   * @param price             The amount to charce the local user
   * @param paymentData       PaymentData from Google Pay that describes the payment method
   * @param badgeRecipient    Who will be getting the badge
   * @param additionalMessage An additional message to send along with the badge (only used if badge recipient is not self)
   */
  fun continuePayment(price: FiatMoney, paymentData: PaymentData, badgeRecipient: RecipientId, additionalMessage: String?, badgeLevel: Long): Completable {
    Log.d(TAG, "Creating payment intent for $price...", true)

    return stripeApi.createPaymentIntent(price, badgeLevel)
      .onErrorResumeNext {
        if (it is DonationError) {
          Single.error(it)
        } else {
          val recipient = Recipient.resolved(badgeRecipient)
          val errorSource = if (recipient.isSelf) DonationErrorSource.BOOST else DonationErrorSource.GIFT
          Single.error(DonationError.getPaymentSetupError(errorSource, it))
        }
      }
      .flatMapCompletable { result ->
        val recipient = Recipient.resolved(badgeRecipient)
        val errorSource = if (recipient.isSelf) DonationErrorSource.BOOST else DonationErrorSource.GIFT

        Log.d(TAG, "Created payment intent for $price.", true)
        when (result) {
          is StripeApi.CreatePaymentIntentResult.AmountIsTooSmall -> Completable.error(DonationError.oneTimeDonationAmountTooSmall(errorSource))
          is StripeApi.CreatePaymentIntentResult.AmountIsTooLarge -> Completable.error(DonationError.oneTimeDonationAmountTooLarge(errorSource))
          is StripeApi.CreatePaymentIntentResult.CurrencyIsNotSupported -> Completable.error(DonationError.invalidCurrencyForOneTimeDonation(errorSource))
          is StripeApi.CreatePaymentIntentResult.Success -> confirmPayment(price, paymentData, result.paymentIntent, badgeRecipient, additionalMessage, badgeLevel)
        }
      }.subscribeOn(Schedulers.io())
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
    return Single
      .fromCallable {
        ApplicationDependencies.getDonationsService()
          .cancelSubscription(localSubscriber.subscriberId)
      }
      .subscribeOn(Schedulers.io())
      .flatMap(ServiceResponse<EmptyResponse>::flattenResult)
      .ignoreElement()
      .doOnComplete { Log.d(TAG, "Cancelled active subscription.", true) }
  }

  fun ensureSubscriberId(): Completable {
    Log.d(TAG, "Ensuring SubscriberId exists on Signal service...", true)
    val subscriberId = SignalStore.donationsValues().getSubscriber()?.subscriberId ?: SubscriberId.generate()
    return Single
      .fromCallable {
        ApplicationDependencies
          .getDonationsService()
          .putSubscription(subscriberId)
      }
      .subscribeOn(Schedulers.io())
      .flatMap(ServiceResponse<EmptyResponse>::flattenResult).ignoreElement()
      .doOnComplete {
        Log.d(TAG, "Successfully set SubscriberId exists on Signal service.", true)

        SignalStore
          .donationsValues()
          .setSubscriber(Subscriber(subscriberId, SignalStore.donationsValues().getSubscriptionCurrency().currencyCode))

        scheduleSyncForAccountRecordChangeSync()
      }
  }

  private fun confirmPayment(price: FiatMoney, paymentData: PaymentData, paymentIntent: StripeApi.PaymentIntent, badgeRecipient: RecipientId, additionalMessage: String?, badgeLevel: Long): Completable {
    val isBoost = badgeRecipient == Recipient.self().id
    val donationErrorSource: DonationErrorSource = if (isBoost) DonationErrorSource.BOOST else DonationErrorSource.GIFT

    Log.d(TAG, "Confirming payment intent...", true)
    val confirmPayment = stripeApi.confirmPaymentIntent(GooglePayPaymentSource(paymentData), paymentIntent).onErrorResumeNext {
      Completable.error(DonationError.getPaymentSetupError(donationErrorSource, it))
    }

    val waitOnRedemption = Completable.create {
      val donationReceiptRecord = if (isBoost) {
        DonationReceiptRecord.createForBoost(price)
      } else {
        DonationReceiptRecord.createForGift(price)
      }

      val donationTypeLabel = donationReceiptRecord.type.code.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString() }

      Log.d(TAG, "Confirmed payment intent. Recording $donationTypeLabel receipt and submitting badge reimbursement job chain.", true)
      SignalDatabase.donationReceipts.addReceipt(donationReceiptRecord)

      val countDownLatch = CountDownLatch(1)
      var finalJobState: JobTracker.JobState? = null
      val chain = if (isBoost) {
        BoostReceiptRequestResponseJob.createJobChainForBoost(paymentIntent)
      } else {
        BoostReceiptRequestResponseJob.createJobChainForGift(paymentIntent, badgeRecipient, additionalMessage, badgeLevel)
      }

      chain.enqueue { _, jobState ->
        if (jobState.isComplete) {
          finalJobState = jobState
          countDownLatch.countDown()
        }
      }

      try {
        if (countDownLatch.await(10, TimeUnit.SECONDS)) {
          when (finalJobState) {
            JobTracker.JobState.SUCCESS -> {
              Log.d(TAG, "$donationTypeLabel request response job chain succeeded.", true)
              it.onComplete()
            }
            JobTracker.JobState.FAILURE -> {
              Log.d(TAG, "$donationTypeLabel request response job chain failed permanently.", true)
              it.onError(DonationError.genericBadgeRedemptionFailure(donationErrorSource))
            }
            else -> {
              Log.d(TAG, "$donationTypeLabel request response job chain ignored due to in-progress jobs.", true)
              it.onError(DonationError.timeoutWaitingForToken(donationErrorSource))
            }
          }
        } else {
          Log.d(TAG, "$donationTypeLabel job chain timed out waiting for job completion.", true)
          it.onError(DonationError.timeoutWaitingForToken(donationErrorSource))
        }
      } catch (e: InterruptedException) {
        Log.d(TAG, "$donationTypeLabel job chain interrupted", e, true)
        it.onError(DonationError.timeoutWaitingForToken(donationErrorSource))
      }
    }

    return confirmPayment.andThen(waitOnRedemption)
  }

  fun setSubscriptionLevel(subscriptionLevel: String): Completable {
    return getOrCreateLevelUpdateOperation(subscriptionLevel)
      .flatMapCompletable { levelUpdateOperation ->
        val subscriber = SignalStore.donationsValues().requireSubscriber()

        Log.d(TAG, "Attempting to set user subscription level to $subscriptionLevel", true)
        Single
          .fromCallable {
            ApplicationDependencies.getDonationsService().updateSubscriptionLevel(
              subscriber.subscriberId,
              subscriptionLevel,
              subscriber.currencyCode,
              levelUpdateOperation.idempotencyKey.serialize(),
              SubscriptionReceiptRequestResponseJob.MUTEX
            )
          }
          .flatMapCompletable {
            if (it.status == 200 || it.status == 204) {
              Log.d(TAG, "Successfully set user subscription to level $subscriptionLevel with response code ${it.status}", true)
              SignalStore.donationsValues().updateLocalStateForLocalSubscribe()
              scheduleSyncForAccountRecordChange()
              LevelUpdate.updateProcessingState(false)
              Completable.complete()
            } else {
              if (it.applicationError.isPresent) {
                Log.w(TAG, "Failed to set user subscription to level $subscriptionLevel with response code ${it.status}", it.applicationError.get(), true)
                SignalStore.donationsValues().clearLevelOperations()
              } else {
                Log.w(TAG, "Failed to set user subscription to level $subscriptionLevel", it.executionError.orElse(null), true)
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
                    it.onError(DonationError.genericBadgeRedemptionFailure(DonationErrorSource.SUBSCRIPTION))
                  }
                  else -> {
                    Log.d(TAG, "Subscription request response job chain ignored due to in-progress jobs.", true)
                    it.onError(DonationError.timeoutWaitingForToken(DonationErrorSource.SUBSCRIPTION))
                  }
                }
              } else {
                Log.d(TAG, "Subscription request response job timed out.", true)
                it.onError(DonationError.timeoutWaitingForToken(DonationErrorSource.SUBSCRIPTION))
              }
            } catch (e: InterruptedException) {
              Log.w(TAG, "Subscription request response interrupted.", e, true)
              it.onError(DonationError.timeoutWaitingForToken(DonationErrorSource.SUBSCRIPTION))
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

  override fun fetchPaymentIntent(price: FiatMoney, level: Long): Single<StripeApi.PaymentIntent> {
    Log.d(TAG, "Fetching payment intent from Signal service for $price... (Locale.US minimum precision: ${price.minimumUnitPrecisionString})")
    return Single
      .fromCallable {
        ApplicationDependencies
          .getDonationsService()
          .createDonationIntentWithAmount(price.minimumUnitPrecisionString, price.currency.currencyCode, level)
      }
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
      .flatMap {
        Single.fromCallable {
          ApplicationDependencies
            .getDonationsService()
            .createSubscriptionPaymentMethod(it.subscriberId)
        }
      }
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
      Single.fromCallable {
        ApplicationDependencies
          .getDonationsService()
          .setDefaultPaymentMethodId(it.subscriberId, paymentMethodId)
      }
    }.flatMap(ServiceResponse<EmptyResponse>::flattenResult).ignoreElement().doOnComplete {
      Log.d(TAG, "Set default payment method via Signal service!")
    }
  }

  companion object {
    private val TAG = Log.tag(DonationPaymentRepository::class.java)
  }
}
