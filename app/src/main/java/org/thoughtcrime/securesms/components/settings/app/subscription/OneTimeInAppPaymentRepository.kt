package org.thoughtcrime.securesms.components.settings.app.subscription

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.boost.Boost
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.InAppPaymentOneTimeContextJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Currency
import java.util.Locale

/**
 * Shared one-time payment methods that apply to both Stripe and PayPal payments.
 */
object OneTimeInAppPaymentRepository {

  private val TAG = Log.tag(OneTimeInAppPaymentRepository::class.java)

  /**
   * Translates the given Throwable into a DonationError
   *
   * If the throwable is already a DonationError, it's returned as is. Otherwise we will return an adequate payment setup error.
   */
  fun handleCreatePaymentIntentErrorSync(throwable: Throwable, badgeRecipient: RecipientId, paymentSourceType: PaymentSourceType): Throwable {
    return if (throwable is DonationError) {
      throwable
    } else {
      val recipient = Recipient.resolved(badgeRecipient)
      val errorSource = if (recipient.isSelf) DonationErrorSource.ONE_TIME else DonationErrorSource.GIFT
      DonationError.getPaymentSetupError(errorSource, throwable, paymentSourceType)
    }
  }

  /**
   * Passthrough Rx wrapper for [handleCreatePaymentIntentErrorSync]. This does not dispatch to a thread-pool.
   */
  fun <T : Any> handleCreatePaymentIntentError(throwable: Throwable, badgeRecipient: RecipientId, paymentSourceType: PaymentSourceType): Single<T> {
    return Single.error(handleCreatePaymentIntentErrorSync(throwable, badgeRecipient, paymentSourceType))
  }

  /**
   * Checks whether the recipient for the given ID is allowed to receive a gift. Returns
   * normally if they are and emits an error otherwise.
   */
  @WorkerThread
  fun verifyRecipientIsAllowedToReceiveAGiftSync(badgeRecipient: RecipientId) {
    Log.d(TAG, "Verifying badge recipient $badgeRecipient", true)
    val recipient = Recipient.resolved(badgeRecipient)

    if (recipient.isSelf) {
      Log.d(TAG, "Cannot send a gift to self.", true)
      throw DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid
    }

    if (!recipient.isIndividual || recipient.registered != RecipientTable.RegisteredState.REGISTERED) {
      Log.w(TAG, "Invalid badge recipient $badgeRecipient. Verification failed.", true)
      throw DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid
    }
  }

  /**
   * Parses the donations configuration and returns any boost information from it. Also maps and filters out currencies
   * based on platform and payment method availability.
   */
  fun getBoosts(): Single<Map<Currency, List<Boost>>> {
    return Single.fromCallable { AppDependencies.donationsService.getDonationsConfiguration(Locale.getDefault()) }
      .subscribeOn(Schedulers.io())
      .flatMap { it.flattenResult() }
      .map { config ->
        config.getBoostAmounts().mapValues { (_, value) ->
          value.map {
            Boost(it)
          }
        }
      }
  }

  /**
   * Get the one-time donation badge from the Signal service
   */
  fun getBoostBadge(): Single<Badge> {
    return Single
      .fromCallable {
        AppDependencies.donationsService
          .getDonationsConfiguration(Locale.getDefault())
      }
      .subscribeOn(Schedulers.io())
      .flatMap { it.flattenResult() }
      .map { it.getBoostBadges().first() }
  }

  /**
   * Get a map of [Currency] to [FiatMoney] representing minimum donation amounts from the
   * signal service. This is scheduled on the io thread-pool.
   */
  fun getMinimumDonationAmounts(): Single<Map<Currency, FiatMoney>> {
    return Single.fromCallable { AppDependencies.donationsService.getDonationsConfiguration(Locale.getDefault()) }
      .flatMap { it.flattenResult() }
      .subscribeOn(Schedulers.io())
      .map { it.getMinimumDonationAmounts() }
  }

  /**
   * Submits the required jobs to redeem the given [InAppPaymentTable.InAppPayment]
   *
   * This job does mutate the in-app payment but since this is the final action during setup,
   * returning that data is useless.
   */
  fun submitRedemptionJobChain(
    inAppPayment: InAppPaymentTable.InAppPayment,
    paymentIntentId: String
  ) {
    Log.d(TAG, "Confirmed payment intent. Submitting badge reimbursement job chain.", true)
    SignalDatabase.inAppPayments.update(
      inAppPayment = inAppPayment.copy(
        data = inAppPayment.data.newBuilder().redemption(
          redemption = InAppPaymentData.RedemptionState(
            stage = InAppPaymentData.RedemptionState.Stage.INIT,
            paymentIntentId = paymentIntentId
          )
        ).build()
      )
    )

    InAppPaymentOneTimeContextJob.createJobChain(inAppPayment).enqueue()
  }
}
