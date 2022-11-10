package org.thoughtcrime.securesms.components.settings.app.subscription

import android.app.Activity
import android.content.Intent
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.GooglePayApi
import org.signal.donations.StripeApi
import org.signal.donations.StripeIntentAccessor
import org.signal.donations.json.StripeIntentStatus
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.Environment
import org.whispersystems.signalservice.api.subscriptions.StripeClientSecret
import org.whispersystems.signalservice.internal.EmptyResponse
import org.whispersystems.signalservice.internal.ServiceResponse

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
class StripeRepository(activity: Activity) : StripeApi.PaymentIntentFetcher, StripeApi.SetupIntentHelper {

  private val googlePayApi = GooglePayApi(activity, StripeApi.Gateway(Environment.Donations.STRIPE_CONFIGURATION), Environment.Donations.GOOGLE_PAY_CONFIGURATION)
  private val stripeApi = StripeApi(Environment.Donations.STRIPE_CONFIGURATION, this, this, ApplicationDependencies.getOkHttpClient())

  fun isGooglePayAvailable(): Completable {
    return googlePayApi.queryIsReadyToPay()
  }

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
   * @param price             The amount to charce the local user
   * @param badgeRecipient    Who will be getting the badge
   */
  fun continuePayment(
    price: FiatMoney,
    badgeRecipient: RecipientId,
    badgeLevel: Long,
  ): Single<StripeIntentAccessor> {
    Log.d(TAG, "Creating payment intent for $price...", true)

    return stripeApi.createPaymentIntent(price, badgeLevel)
      .onErrorResumeNext {
        handleCreatePaymentIntentError(it, badgeRecipient)
      }
      .flatMap { result ->
        val recipient = Recipient.resolved(badgeRecipient)
        val errorSource = if (recipient.isSelf) DonationErrorSource.BOOST else DonationErrorSource.GIFT

        Log.d(TAG, "Created payment intent for $price.", true)
        when (result) {
          is StripeApi.CreatePaymentIntentResult.AmountIsTooSmall -> Single.error(DonationError.oneTimeDonationAmountTooSmall(errorSource))
          is StripeApi.CreatePaymentIntentResult.AmountIsTooLarge -> Single.error(DonationError.oneTimeDonationAmountTooLarge(errorSource))
          is StripeApi.CreatePaymentIntentResult.CurrencyIsNotSupported -> Single.error(DonationError.invalidCurrencyForOneTimeDonation(errorSource))
          is StripeApi.CreatePaymentIntentResult.Success -> Single.just(result.paymentIntent)
        }
      }.subscribeOn(Schedulers.io())
  }

  fun createAndConfirmSetupIntent(paymentSource: StripeApi.PaymentSource): Single<StripeApi.Secure3DSAction> {
    Log.d(TAG, "Continuing subscription setup...", true)
    return stripeApi.createSetupIntent()
      .flatMap { result ->
        Log.d(TAG, "Retrieved SetupIntent, confirming...", true)
        stripeApi.confirmSetupIntent(paymentSource, result.setupIntent)
      }
  }

  fun confirmPayment(
    paymentSource: StripeApi.PaymentSource,
    paymentIntent: StripeIntentAccessor,
    badgeRecipient: RecipientId
  ): Single<StripeApi.Secure3DSAction> {
    val isBoost = badgeRecipient == Recipient.self().id
    val donationErrorSource: DonationErrorSource = if (isBoost) DonationErrorSource.BOOST else DonationErrorSource.GIFT

    Log.d(TAG, "Confirming payment intent...", true)
    return stripeApi.confirmPaymentIntent(paymentSource, paymentIntent)
      .onErrorResumeNext {
        Single.error(DonationError.getPaymentSetupError(donationErrorSource, it))
      }
  }

  override fun fetchPaymentIntent(price: FiatMoney, level: Long): Single<StripeIntentAccessor> {
    Log.d(TAG, "Fetching payment intent from Signal service for $price... (Locale.US minimum precision: ${price.minimumUnitPrecisionString})")
    return Single
      .fromCallable {
        ApplicationDependencies
          .getDonationsService()
          .createDonationIntentWithAmount(price.minimumUnitPrecisionString, price.currency.currencyCode, level)
      }
      .flatMap(ServiceResponse<StripeClientSecret>::flattenResult)
      .map {
        StripeIntentAccessor(
          objectType = StripeIntentAccessor.ObjectType.PAYMENT_INTENT,
          intentId = it.id,
          intentClientSecret = it.clientSecret
        )
      }.doOnSuccess {
        Log.d(TAG, "Got payment intent from Signal service!")
      }
  }

  override fun fetchSetupIntent(): Single<StripeIntentAccessor> {
    Log.d(TAG, "Fetching setup intent from Signal service...")
    return Single.fromCallable { SignalStore.donationsValues().requireSubscriber() }
      .flatMap {
        Single.fromCallable {
          ApplicationDependencies
            .getDonationsService()
            .createStripeSubscriptionPaymentMethod(it.subscriberId)
        }
      }
      .flatMap(ServiceResponse<StripeClientSecret>::flattenResult)
      .map {
        StripeIntentAccessor(
          objectType = StripeIntentAccessor.ObjectType.SETUP_INTENT,
          intentId = it.id,
          intentClientSecret = it.clientSecret
        )
      }
      .doOnSuccess {
        Log.d(TAG, "Got setup intent from Signal service!")
      }
  }

  // We need to get the status and payment id from the intent.

  fun getStatusAndPaymentMethodId(stripeIntentAccessor: StripeIntentAccessor): Single<StatusAndPaymentMethodId> {
    return Single.fromCallable {
      when (stripeIntentAccessor.objectType) {
        StripeIntentAccessor.ObjectType.NONE -> StatusAndPaymentMethodId(StripeIntentStatus.SUCCEEDED, null)
        StripeIntentAccessor.ObjectType.PAYMENT_INTENT -> stripeApi.getPaymentIntent(stripeIntentAccessor).let {
          StatusAndPaymentMethodId(it.status, it.paymentMethod)
        }
        StripeIntentAccessor.ObjectType.SETUP_INTENT -> stripeApi.getSetupIntent(stripeIntentAccessor).let {
          StatusAndPaymentMethodId(it.status, it.paymentMethod)
        }
      }
    }
  }

  fun setDefaultPaymentMethod(paymentMethodId: String): Completable {
    return Single.fromCallable {
      Log.d(TAG, "Getting the subscriber...")
      SignalStore.donationsValues().requireSubscriber()
    }.flatMap {
      Log.d(TAG, "Setting default payment method via Signal service...")
      Single.fromCallable {
        ApplicationDependencies
          .getDonationsService()
          .setDefaultStripePaymentMethod(it.subscriberId, paymentMethodId)
      }
    }.flatMap(ServiceResponse<EmptyResponse>::flattenResult).ignoreElement().doOnComplete {
      Log.d(TAG, "Set default payment method via Signal service!")
    }
  }

  fun createCreditCardPaymentSource(donationErrorSource: DonationErrorSource, cardData: StripeApi.CardData): Single<StripeApi.PaymentSource> {
    Log.d(TAG, "Creating credit card payment source via Stripe api...")
    return stripeApi.createPaymentSourceFromCardData(cardData).map {
      when (it) {
        is StripeApi.CreatePaymentSourceFromCardDataResult.Failure -> throw DonationError.getPaymentSetupError(donationErrorSource, it.reason)
        is StripeApi.CreatePaymentSourceFromCardDataResult.Success -> it.paymentSource
      }
    }
  }

  data class StatusAndPaymentMethodId(
    val status: StripeIntentStatus,
    val paymentMethod: String?
  )

  companion object {
    private val TAG = Log.tag(StripeRepository::class.java)

    fun <T> handleCreatePaymentIntentError(throwable: Throwable, badgeRecipient: RecipientId): Single<T> {
      return if (throwable is DonationError) {
        Single.error(throwable)
      } else {
        val recipient = Recipient.resolved(badgeRecipient)
        val errorSource = if (recipient.isSelf) DonationErrorSource.BOOST else DonationErrorSource.GIFT
        Single.error(DonationError.getPaymentSetupError(errorSource, throwable))
      }
    }
  }
}
