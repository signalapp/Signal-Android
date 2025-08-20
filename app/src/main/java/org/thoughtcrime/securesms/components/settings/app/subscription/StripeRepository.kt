package org.thoughtcrime.securesms.components.settings.app.subscription

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSource
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeApi
import org.signal.donations.StripeIntentAccessor
import org.signal.donations.json.StripeIntentStatus
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentMethodType
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError.OneTimeDonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.Environment
import org.whispersystems.signalservice.api.subscriptions.StripeClientSecret

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
object StripeRepository : StripeApi.PaymentIntentFetcher, StripeApi.SetupIntentHelper {

  private val TAG = Log.tag(StripeRepository::class.java)

  private val stripeApi = StripeApi(Environment.Donations.STRIPE_CONFIGURATION, this, this, AppDependencies.okHttpClient)

  /**
   * Utilize the [StripeApi] to create a payment intent
   *
   * @return a [StripeIntentAccessor] that can be used to address the payment intent.
   */
  @WorkerThread
  fun createPaymentIntent(
    price: FiatMoney,
    badgeRecipient: RecipientId,
    badgeLevel: Long,
    paymentSourceType: PaymentSourceType
  ): StripeIntentAccessor {
    check(paymentSourceType is PaymentSourceType.Stripe)
    Log.d(TAG, "Creating payment intent for $price...", true)

    val result: StripeApi.CreatePaymentIntentResult = try {
      stripeApi.createPaymentIntent(price, badgeLevel, paymentSourceType)
    } catch (e: Exception) {
      throw OneTimeInAppPaymentRepository.handleCreatePaymentIntentErrorSync(e, badgeRecipient, paymentSourceType)
    }

    val recipient = Recipient.resolved(badgeRecipient)
    val errorSource = if (recipient.isSelf) DonationErrorSource.ONE_TIME else DonationErrorSource.GIFT

    Log.d(TAG, "Created payment intent for $price.", true)
    when (result) {
      is StripeApi.CreatePaymentIntentResult.AmountIsTooSmall -> throw OneTimeDonationError.AmountTooSmallError(errorSource)
      is StripeApi.CreatePaymentIntentResult.AmountIsTooLarge -> throw OneTimeDonationError.AmountTooLargeError(errorSource)
      is StripeApi.CreatePaymentIntentResult.CurrencyIsNotSupported -> throw OneTimeDonationError.InvalidCurrencyError(errorSource)
      is StripeApi.CreatePaymentIntentResult.Success -> return result.paymentIntent
    }
  }

  /**
   * Confirms the given payment [paymentIntent] via the Stripe API
   *
   * @return a required action, if necessary. This can be the case for some credit cards as well as iDEAL transactions.
   */
  @WorkerThread
  fun confirmPaymentIntent(
    paymentSource: PaymentSource,
    paymentIntent: StripeIntentAccessor,
    badgeRecipient: RecipientId
  ): StripeApi.Secure3DSAction {
    val isBoost = badgeRecipient == Recipient.self().id
    val donationErrorSource: DonationErrorSource = if (isBoost) DonationErrorSource.ONE_TIME else DonationErrorSource.GIFT

    Log.d(TAG, "Confirming payment intent...", true)

    try {
      return stripeApi.confirmPaymentIntent(paymentSource, paymentIntent)
    } catch (e: Exception) {
      throw DonationError.getPaymentSetupError(donationErrorSource, e, paymentSource.type)
    }
  }

  /**
   * Creates and confirms a setup intent for a new subscription via the [StripeApi].
   *
   * @return a required action, if necessary. This can be the case for some credit cards as well as iDEAL transactions.
   */
  @WorkerThread
  fun createAndConfirmSetupIntent(
    inAppPaymentType: InAppPaymentType,
    paymentSource: PaymentSource,
    paymentSourceType: PaymentSourceType.Stripe
  ): StripeApi.Secure3DSAction {
    Log.d(TAG, "Continuing subscription setup...", true)
    val result: StripeApi.CreateSetupIntentResult = stripeApi.createSetupIntent(inAppPaymentType, paymentSourceType)

    Log.d(TAG, "Retrieved SetupIntent, confirming...", true)
    return stripeApi.confirmSetupIntent(paymentSource, result.setupIntent)
  }

  @WorkerThread
  override fun fetchPaymentIntent(price: FiatMoney, level: Long, sourceType: PaymentSourceType.Stripe): StripeIntentAccessor {
    Log.d(TAG, "Fetching payment intent from Signal service for $price... (Locale.US minimum precision: ${price.minimumUnitPrecisionString})")
    val response = AppDependencies
      .donationsService
      .createDonationIntentWithAmount(price.minimumUnitPrecisionString, price.currency.currencyCode, level, sourceType.paymentMethod)
      .resultOrThrow

    val accessor = StripeIntentAccessor(
      objectType = StripeIntentAccessor.ObjectType.PAYMENT_INTENT,
      intentId = response.id,
      intentClientSecret = response.clientSecret
    )

    Log.d(TAG, "Got payment intent from Signal service!")
    return accessor
  }

  @WorkerThread
  override fun fetchSetupIntent(inAppPaymentType: InAppPaymentType, sourceType: PaymentSourceType.Stripe): StripeIntentAccessor {
    Log.d(TAG, "Fetching setup intent from Signal service...")
    val clientSecret = createPaymentMethod(inAppPaymentType.requireSubscriberType(), sourceType)
    val accessor = StripeIntentAccessor(
      objectType = StripeIntentAccessor.ObjectType.SETUP_INTENT,
      intentId = clientSecret.id,
      intentClientSecret = clientSecret.clientSecret
    )

    Log.d(TAG, "Got setup intent from Signal service!")

    return accessor
  }

  /**
   * Note: There seem to be times when PaymentIntent does not return a status. In these cases, we assume
   *       that we are successful and proceed as normal. If the payment didn't actually succeed, then we
   *       expect an error later in the chain to inform us of this.
   */
  @WorkerThread
  fun getStatusAndPaymentMethodId(
    stripeIntentAccessor: StripeIntentAccessor,
    paymentMethodId: String?
  ): StatusAndPaymentMethodId {
    return when (stripeIntentAccessor.objectType) {
      StripeIntentAccessor.ObjectType.NONE -> StatusAndPaymentMethodId(stripeIntentAccessor.intentId, StripeIntentStatus.SUCCEEDED, paymentMethodId)
      StripeIntentAccessor.ObjectType.PAYMENT_INTENT -> stripeApi.getPaymentIntent(stripeIntentAccessor).let {
        if (it.status == null) {
          Log.d(TAG, "Returned payment intent had a null status.", true)
        }
        StatusAndPaymentMethodId(stripeIntentAccessor.intentId, it.status ?: StripeIntentStatus.SUCCEEDED, it.paymentMethod)
      }

      StripeIntentAccessor.ObjectType.SETUP_INTENT -> stripeApi.getSetupIntent(stripeIntentAccessor).let {
        StatusAndPaymentMethodId(stripeIntentAccessor.intentId, it.status, it.paymentMethodId)
      }
    }
  }

  /**
   * Sets the default payment method for the given subscriber type via Signal Service
   * which will ensure that the user's subscription can be set up properly.
   */
  @WorkerThread
  fun setDefaultPaymentMethod(
    paymentMethodId: String,
    setupIntentId: String,
    subscriberType: InAppPaymentSubscriberRecord.Type,
    paymentSourceType: PaymentSourceType
  ) {
    Log.d(TAG, "Getting the subscriber...")
    val subscriber = InAppPaymentsRepository.requireSubscriber(subscriberType)

    Log.d(TAG, "Setting default payment method via Signal service...")
    if (paymentSourceType == PaymentSourceType.Stripe.IDEAL) {
      AppDependencies
        .donationsService
        .setDefaultIdealPaymentMethod(subscriber.subscriberId, setupIntentId)
    } else {
      AppDependencies
        .donationsService
        .setDefaultStripePaymentMethod(subscriber.subscriberId, paymentMethodId)
    }.resultOrThrow

    Log.d(TAG, "Set default payment method via Signal service!")
    Log.d(TAG, "Storing the subscription payment source type locally.")
    SignalDatabase.inAppPaymentSubscribers.setPaymentMethod(subscriber.subscriberId, paymentSourceType.toPaymentMethodType())
  }

  /**
   * Utilizes the StripeApi to create a [PaymentSource] for the given [StripeApi.CardData]
   */
  fun createCreditCardPaymentSource(donationErrorSource: DonationErrorSource, cardData: StripeApi.CardData): Single<PaymentSource> {
    Log.d(TAG, "Creating credit card payment source via Stripe api...")
    return stripeApi.createPaymentSourceFromCardData(cardData).map {
      when (it) {
        is StripeApi.CreatePaymentSourceFromCardDataResult.Failure -> throw DonationError.getPaymentSetupError(donationErrorSource, it.reason, PaymentSourceType.Stripe.CreditCard)
        is StripeApi.CreatePaymentSourceFromCardDataResult.Success -> it.paymentSource
      }
    }
  }

  /**
   * Utilizes the StripeApi to create a [PaymentSource] for the given [StripeApi.SEPADebitData]
   */
  fun createSEPADebitPaymentSource(sepaDebitData: StripeApi.SEPADebitData): Single<PaymentSource> {
    Log.d(TAG, "Creating SEPA Debit payment source via Stripe api...")
    return stripeApi.createPaymentSourceFromSEPADebitData(sepaDebitData)
  }

  /**
   * Utilizes the StripeApi to create a [PaymentSource] for the given [StripeApi.IDEALData]
   */
  fun createIdealPaymentSource(idealData: StripeApi.IDEALData): Single<PaymentSource> {
    Log.d(TAG, "Creating iDEAL payment source via Stripe api...")
    return stripeApi.createPaymentSourceFromIDEALData(idealData)
  }

  /**
   * Creates the PaymentMethod via the Signal Service. Note that if the operation fails with a 409,
   * it means that the PaymentMethod is already tied to a PayPal account. We can retry in this
   * situation by simply deleting the old subscriber id on the service and replacing it.
   */
  private fun createPaymentMethod(subscriberType: InAppPaymentSubscriberRecord.Type, paymentSourceType: PaymentSourceType.Stripe, retryOn409: Boolean = true): StripeClientSecret {
    val subscriber = InAppPaymentsRepository.requireSubscriber(subscriberType)
    val response = AppDependencies
      .donationsService
      .createStripeSubscriptionPaymentMethod(subscriber.subscriberId, paymentSourceType.paymentMethod)

    return if (retryOn409 && response.status == 409) {
      RecurringInAppPaymentRepository.rotateSubscriberIdSync(subscriberType)
      createPaymentMethod(subscriberType, paymentSourceType, retryOn409 = false)
    } else {
      response.resultOrThrow
    }
  }

  data class StatusAndPaymentMethodId(
    val intentId: String,
    val status: StripeIntentStatus,
    val paymentMethod: String?
  )
}
