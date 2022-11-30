package org.thoughtcrime.securesms.components.settings.app.subscription

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.boost.Boost
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.BoostReceiptRequestResponseJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.PlatformCurrencyUtil
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.DonationProcessor
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OneTimeDonationRepository(private val donationsService: DonationsService) {

  companion object {
    private val TAG = Log.tag(OneTimeDonationRepository::class.java)

    fun <T> handleCreatePaymentIntentError(throwable: Throwable, badgeRecipient: RecipientId, paymentSourceType: PaymentSourceType): Single<T> {
      return if (throwable is DonationError) {
        Single.error(throwable)
      } else {
        val recipient = Recipient.resolved(badgeRecipient)
        val errorSource = if (recipient.isSelf) DonationErrorSource.BOOST else DonationErrorSource.GIFT
        Single.error(DonationError.getPaymentSetupError(errorSource, throwable, paymentSourceType))
      }
    }
  }

  fun getBoosts(): Single<Map<Currency, List<Boost>>> {
    return Single.fromCallable { donationsService.boostAmounts }
      .subscribeOn(Schedulers.io())
      .flatMap(ServiceResponse<Map<String, List<BigDecimal>>>::flattenResult)
      .map { result ->
        result
          .filter { PlatformCurrencyUtil.getAvailableCurrencyCodes().contains(it.key) }
          .mapKeys { (code, _) -> Currency.getInstance(code) }
          .mapValues { (currency, prices) -> prices.map { Boost(FiatMoney(it, currency)) } }
      }
  }

  fun getBoostBadge(): Single<Badge> {
    return Single
      .fromCallable {
        ApplicationDependencies.getDonationsService()
          .getBoostBadge(Locale.getDefault())
      }
      .subscribeOn(Schedulers.io())
      .flatMap(ServiceResponse<SignalServiceProfile.Badge>::flattenResult)
      .map(Badges::fromServiceBadge)
  }

  fun waitForOneTimeRedemption(
    price: FiatMoney,
    paymentIntentId: String,
    badgeRecipient: RecipientId,
    additionalMessage: String?,
    badgeLevel: Long,
    donationProcessor: DonationProcessor
  ): Completable {
    val isBoost = badgeRecipient == Recipient.self().id
    val donationErrorSource: DonationErrorSource = if (isBoost) DonationErrorSource.BOOST else DonationErrorSource.GIFT

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
        BoostReceiptRequestResponseJob.createJobChainForBoost(paymentIntentId, donationProcessor)
      } else {
        BoostReceiptRequestResponseJob.createJobChainForGift(paymentIntentId, badgeRecipient, additionalMessage, badgeLevel, donationProcessor)
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

    return waitOnRedemption
  }
}
