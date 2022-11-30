package org.thoughtcrime.securesms.components.settings.app.subscription

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.money.FiatMoney
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal.PayPalConfirmationResult
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.api.subscriptions.PayPalConfirmPaymentIntentResponse
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentIntentResponse
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentMethodResponse
import java.util.Locale

/**
 * Repository that deals directly with PayPal API calls. Since we don't interact with the PayPal APIs directly (yet)
 * we can do everything here in one place.
 */
class PayPalRepository(private val donationsService: DonationsService) {

  companion object {
    const val ONE_TIME_RETURN_URL = "https://signaldonations.org/return/onetime"
    const val MONTHLY_RETURN_URL = "https://signaldonations.org/return/monthly"
    const val CANCEL_URL = "https://signaldonations.org/cancel"
  }

  fun createOneTimePaymentIntent(
    amount: FiatMoney,
    badgeRecipient: RecipientId,
    badgeLevel: Long
  ): Single<PayPalCreatePaymentIntentResponse> {
    return Single.fromCallable {
      donationsService
        .createPayPalOneTimePaymentIntent(
          Locale.getDefault(),
          amount.currency.currencyCode,
          amount.minimumUnitPrecisionString,
          badgeLevel,
          ONE_TIME_RETURN_URL,
          CANCEL_URL
        )
    }
      .flatMap { it.flattenResult() }
      .onErrorResumeNext { OneTimeDonationRepository.handleCreatePaymentIntentError(it, badgeRecipient, PaymentSourceType.PayPal) }
      .subscribeOn(Schedulers.io())
  }

  fun confirmOneTimePaymentIntent(
    amount: FiatMoney,
    badgeLevel: Long,
    paypalConfirmationResult: PayPalConfirmationResult
  ): Single<PayPalConfirmPaymentIntentResponse> {
    return Single.fromCallable {
      donationsService
        .confirmPayPalOneTimePaymentIntent(
          amount.currency.currencyCode,
          amount.minimumUnitPrecisionString,
          badgeLevel,
          paypalConfirmationResult.payerId,
          paypalConfirmationResult.paymentId,
          paypalConfirmationResult.paymentToken
        )
    }.flatMap { it.flattenResult() }.subscribeOn(Schedulers.io())
  }

  fun createPaymentMethod(): Single<PayPalCreatePaymentMethodResponse> {
    return Single.fromCallable {
      donationsService.createPayPalPaymentMethod(
        Locale.getDefault(),
        SignalStore.donationsValues().requireSubscriber().subscriberId,
        MONTHLY_RETURN_URL,
        CANCEL_URL
      )
    }.flatMap { it.flattenResult() }.subscribeOn(Schedulers.io())
  }

  fun setDefaultPaymentMethod(paymentMethodId: String): Completable {
    return Single.fromCallable {
      donationsService.setDefaultPayPalPaymentMethod(
        SignalStore.donationsValues().requireSubscriber().subscriberId,
        paymentMethodId
      )
    }.flatMap { it.flattenResult() }.ignoreElement().andThen {
      SignalStore.donationsValues().setSubscriptionPaymentSourceType(PaymentSourceType.PayPal)
    }.subscribeOn(Schedulers.io())
  }
}
