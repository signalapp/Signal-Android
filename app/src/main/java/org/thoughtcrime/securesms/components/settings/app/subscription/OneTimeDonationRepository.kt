package org.thoughtcrime.securesms.components.settings.app.subscription

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.boost.Boost
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayRequest
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.BoostReceiptRequestResponseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.internal.push.DonationProcessor
import java.util.Currency
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OneTimeDonationRepository(private val donationsService: DonationsService) {

  companion object {
    private val TAG = Log.tag(OneTimeDonationRepository::class.java)

    fun <T : Any> handleCreatePaymentIntentError(throwable: Throwable, badgeRecipient: RecipientId, paymentSourceType: PaymentSourceType): Single<T> {
      return if (throwable is DonationError) {
        Single.error(throwable)
      } else {
        val recipient = Recipient.resolved(badgeRecipient)
        val errorSource = if (recipient.isSelf) DonationErrorSource.ONE_TIME else DonationErrorSource.GIFT
        Single.error(DonationError.getPaymentSetupError(errorSource, throwable, paymentSourceType))
      }
    }

    fun verifyRecipientIsAllowedToReceiveAGift(badgeRecipient: RecipientId): Completable {
      return Completable.fromAction {
        Log.d(TAG, "Verifying badge recipient $badgeRecipient", true)
        val recipient = Recipient.resolved(badgeRecipient)

        if (recipient.isSelf) {
          Log.d(TAG, "Cannot send a gift to self.", true)
          throw DonationError.GiftRecipientVerificationError.SelectedRecipientDoesNotSupportGifts
        }

        if (recipient.isGroup || recipient.isDistributionList || recipient.registered != RecipientTable.RegisteredState.REGISTERED) {
          Log.w(TAG, "Invalid badge recipient $badgeRecipient. Verification failed.", true)
          throw DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid
        }
      }.subscribeOn(Schedulers.io())
    }
  }

  fun getBoosts(): Single<Map<Currency, List<Boost>>> {
    return Single.fromCallable { donationsService.getDonationsConfiguration(Locale.getDefault()) }
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

  fun getBoostBadge(): Single<Badge> {
    return Single
      .fromCallable {
        ApplicationDependencies.getDonationsService()
          .getDonationsConfiguration(Locale.getDefault())
      }
      .subscribeOn(Schedulers.io())
      .flatMap { it.flattenResult() }
      .map { it.getBoostBadges().first() }
  }

  fun getMinimumDonationAmounts(): Single<Map<Currency, FiatMoney>> {
    return Single.fromCallable { donationsService.getDonationsConfiguration(Locale.getDefault()) }
      .flatMap { it.flattenResult() }
      .subscribeOn(Schedulers.io())
      .map { it.getMinimumDonationAmounts() }
  }

  fun waitForOneTimeRedemption(
    gatewayRequest: GatewayRequest,
    paymentIntentId: String,
    donationProcessor: DonationProcessor,
    paymentSourceType: PaymentSourceType
  ): Completable {
    val isLongRunning = paymentSourceType == PaymentSourceType.Stripe.SEPADebit
    val isBoost = gatewayRequest.recipientId == Recipient.self().id
    val donationErrorSource: DonationErrorSource = if (isBoost) DonationErrorSource.ONE_TIME else DonationErrorSource.GIFT

    val waitOnRedemption = Completable.create {
      val donationReceiptRecord = if (isBoost) {
        DonationReceiptRecord.createForBoost(gatewayRequest.fiat)
      } else {
        DonationReceiptRecord.createForGift(gatewayRequest.fiat)
      }

      val donationTypeLabel = donationReceiptRecord.type.code.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString() }

      Log.d(TAG, "Confirmed payment intent. Recording $donationTypeLabel receipt and submitting badge reimbursement job chain.", true)
      SignalDatabase.donationReceipts.addReceipt(donationReceiptRecord)

      SignalStore.donationsValues().setPendingOneTimeDonation(
        DonationSerializationHelper.createPendingOneTimeDonationProto(
          gatewayRequest.badge,
          paymentSourceType,
          gatewayRequest.fiat
        )
      )

      val terminalDonation = TerminalDonationQueue.TerminalDonation(
        level = gatewayRequest.level,
        isLongRunningPaymentMethod = isLongRunning
      )

      val countDownLatch = CountDownLatch(1)
      var finalJobState: JobTracker.JobState? = null
      val chain = if (isBoost) {
        BoostReceiptRequestResponseJob.createJobChainForBoost(paymentIntentId, donationProcessor, gatewayRequest.uiSessionKey, terminalDonation)
      } else {
        BoostReceiptRequestResponseJob.createJobChainForGift(paymentIntentId, gatewayRequest.recipientId, gatewayRequest.additionalMessage, gatewayRequest.level, donationProcessor, gatewayRequest.uiSessionKey, terminalDonation)
      }

      chain.enqueue { _, jobState ->
        if (jobState.isComplete) {
          finalJobState = jobState
          countDownLatch.countDown()
        }
      }

      val timeoutError: DonationError = if (isLongRunning) {
        DonationError.donationPending(donationErrorSource, gatewayRequest)
      } else {
        DonationError.timeoutWaitingForToken(donationErrorSource)
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
              it.onError(timeoutError)
            }
          }
        } else {
          Log.d(TAG, "$donationTypeLabel job chain timed out waiting for job completion.", true)
          it.onError(timeoutError)
        }
      } catch (e: InterruptedException) {
        Log.d(TAG, "$donationTypeLabel job chain interrupted", e, true)
        it.onError(timeoutError)
      }
    }

    return waitOnRedemption
  }
}
