/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.annotation.CheckResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSource
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError.BadgeRedemptionError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.InAppPaymentPayPalOneTimeSetupJob
import org.thoughtcrime.securesms.jobs.InAppPaymentPayPalRecurringSetupJob
import org.thoughtcrime.securesms.jobs.InAppPaymentStripeOneTimeSetupJob
import org.thoughtcrime.securesms.jobs.InAppPaymentStripeRecurringSetupJob
import java.util.concurrent.TimeUnit

/**
 * Allows a fragment to display UI to the user to complete some action. When the action is completed,
 * it is expected that the InAppPayment for the given id is in the [InAppPaymentTable.State.REQUIRED_ACTION_COMPLETED]
 * state with the appropriate [InAppPaymentData] completion field set.
 */
typealias RequiredActionHandler = (InAppPaymentTable.InAppPaymentId) -> Completable

/**
 * Shared logic between paypal and stripe for initiating transactions and awaiting token
 * redemption.
 */
object SharedInAppPaymentPipeline {

  private val TAG = Log.tag(SharedInAppPaymentPipeline::class)

  /**
   * Awaits completion of the transaction with Stripe.
   *
   * This method will enqueue the proper setup job based off the type of [InAppPaymentTable.InAppPayment] and then
   * await for either [InAppPaymentTable.State.PENDING], [InAppPaymentTable.State.REQUIRES_ACTION] or [InAppPaymentTable.State.END]
   * before moving further, handling each state appropriately.
   */
  @CheckResult
  fun awaitTransaction(
    inAppPaymentId: InAppPaymentTable.InAppPaymentId,
    paymentSource: PaymentSource,
    oneTimeRequiredActionHandler: RequiredActionHandler,
    monthlyRequiredActionHandler: RequiredActionHandler
  ): Single<InAppPaymentTable.InAppPayment> {
    return InAppPaymentsRepository.observeUpdates(inAppPaymentId)
      .doOnSubscribe {
        val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)!!
        val job = if (inAppPayment.type.recurring) {
          if (inAppPayment.data.paymentMethodType == InAppPaymentData.PaymentMethodType.PAYPAL) {
            InAppPaymentPayPalRecurringSetupJob.create(inAppPayment, paymentSource)
          } else {
            InAppPaymentStripeRecurringSetupJob.create(inAppPayment, paymentSource)
          }
        } else {
          if (inAppPayment.data.paymentMethodType == InAppPaymentData.PaymentMethodType.PAYPAL) {
            InAppPaymentPayPalOneTimeSetupJob.create(inAppPayment, paymentSource)
          } else {
            InAppPaymentStripeOneTimeSetupJob.create(inAppPayment, paymentSource)
          }
        }

        AppDependencies.jobManager.add(job)
      }
      .skipWhile { it.state != InAppPaymentTable.State.PENDING && it.state != InAppPaymentTable.State.REQUIRES_ACTION && it.state != InAppPaymentTable.State.END }
      .firstOrError()
      .flatMap { iap ->
        when (iap.state) {
          InAppPaymentTable.State.PENDING -> {
            Log.w(TAG, "Payment of type ${iap.type} is pending. Awaiting completion.")
            awaitRedemption(iap, paymentSource.type)
          }

          InAppPaymentTable.State.REQUIRES_ACTION -> {
            Log.d(TAG, "Payment of type ${iap.type} requires user action to set up.", true)

            val requiredActionHandler = if (iap.type.recurring) monthlyRequiredActionHandler else oneTimeRequiredActionHandler
            requiredActionHandler(iap.id).andThen(awaitTransaction(iap.id, paymentSource, oneTimeRequiredActionHandler, monthlyRequiredActionHandler))
          }

          InAppPaymentTable.State.END -> {
            if (iap.data.error != null) {
              Log.d(TAG, "IAP error detected.", true)
              Single.error(InAppPaymentError(iap.data.error))
            } else {
              Log.d(TAG, "Unexpected early end state. Possible payment failure.", true)
              Single.error(DonationError.genericPaymentFailure(iap.type.toErrorSource()))
            }
          }

          else -> error("Unexpected state ${iap.state}")
        }
      }
  }

  /**
   * Waits 10 seconds for the redemption to complete, and fails with a temporary error afterwards.
   */
  @CheckResult
  fun awaitRedemption(inAppPayment: InAppPaymentTable.InAppPayment, paymentSourceType: PaymentSourceType): Single<InAppPaymentTable.InAppPayment> {
    val isLongRunning = paymentSourceType.isBankTransfer
    val errorSource = when (inAppPayment.type) {
      InAppPaymentType.UNKNOWN -> error("Unsupported type UNKNOWN.")
      InAppPaymentType.ONE_TIME_GIFT -> DonationErrorSource.GIFT
      InAppPaymentType.ONE_TIME_DONATION -> DonationErrorSource.ONE_TIME
      InAppPaymentType.RECURRING_DONATION -> DonationErrorSource.MONTHLY
      InAppPaymentType.RECURRING_BACKUP -> error("Unsupported BACKUP.")
    }

    val timeoutError = if (isLongRunning) {
      BadgeRedemptionError.DonationPending(errorSource, inAppPayment)
    } else {
      BadgeRedemptionError.TimeoutWaitingForTokenError(errorSource)
    }

    return InAppPaymentsRepository.observeUpdates(inAppPayment.id).filter {
      it.state == InAppPaymentTable.State.END
    }.take(1).map {
      if (it.data.error != null) {
        Log.d(TAG, "Failure during redemption chain: ${it.data.error}", true)
        throw InAppPaymentError(it.data.error)
      }
      it
    }.firstOrError().timeout(10, TimeUnit.SECONDS, Single.error(timeoutError))
  }
}
