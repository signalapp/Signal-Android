/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.annotation.SuppressLint
import org.signal.core.util.logging.Log
import org.signal.donations.PaymentSource
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeApi
import org.signal.donations.StripeIntentAccessor
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.toPaymentSource
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.protos.InAppPaymentSetupJobData

/**
 *  Handles setup of recurring Stripe transactions.
 */
class InAppPaymentStripeRecurringSetupJob private constructor(
  data: InAppPaymentSetupJobData,
  parameters: Parameters
) : InAppPaymentSetupJob(data, parameters) {

  companion object {
    const val KEY = "InAppPaymentStripeRecurringSetupJob"
    private val TAG = Log.tag(InAppPaymentStripeRecurringSetupJob::class)

    /**
     * Creates a new job for performing stripe recurring payment setup. Note that
     * we do not require network for this job, as if the network is not present, we
     * should treat that as an immediate error and fail the job.
     */
    fun create(
      inAppPayment: InAppPaymentTable.InAppPayment,
      paymentSource: PaymentSource
    ): InAppPaymentStripeRecurringSetupJob {
      return InAppPaymentStripeRecurringSetupJob(
        getJobData(inAppPayment, paymentSource),
        getParameters(inAppPayment)
      )
    }
  }

  override fun run(): Result {
    return synchronized(InAppPaymentsRepository.resolveLock(InAppPaymentTable.InAppPaymentId(data.inAppPaymentId))) {
      performTransaction()
    }
  }

  override fun performPreUserAction(inAppPayment: InAppPaymentTable.InAppPayment): RequiredUserAction {
    info("Ensuring the subscriber id is set on the server.")
    RecurringInAppPaymentRepository.ensureSubscriberIdSync(inAppPayment.type.requireSubscriberType())
    info("Canceling active subscription (if necessary).")
    RecurringInAppPaymentRepository.cancelActiveSubscriptionIfNecessarySync(inAppPayment.type.requireSubscriberType())
    info("Creating and confirming setup intent.")
    return when (val action = StripeRepository.createAndConfirmSetupIntent(inAppPayment.type, data.inAppPaymentSource!!.toPaymentSource(), inAppPayment.data.paymentMethodType.toPaymentSourceType() as PaymentSourceType.Stripe)) {
      is StripeApi.Secure3DSAction.ConfirmRequired -> RequiredUserAction.StripeActionRequired(action)
      is StripeApi.Secure3DSAction.NotNeeded -> RequiredUserAction.StripeActionNotRequired(action)
    }
  }

  @SuppressLint("CheckResult")
  override fun performPostUserAction(inAppPayment: InAppPaymentTable.InAppPayment): Result {
    val paymentMethodId = inAppPayment.data.stripeActionComplete!!.paymentMethodId
    val intentAccessor = StripeIntentAccessor(
      objectType = StripeIntentAccessor.ObjectType.SETUP_INTENT,
      intentId = inAppPayment.data.stripeActionComplete.stripeIntentId,
      intentClientSecret = inAppPayment.data.stripeActionComplete.stripeClientSecret
    )

    info("Requesting status and payment method id from stripe service.")
    val statusAndPaymentMethodId = StripeRepository.getStatusAndPaymentMethodId(intentAccessor, paymentMethodId)

    if (!statusAndPaymentMethodId.status.canProceed()) {
      warning("Cannot proceed with status ${statusAndPaymentMethodId.status}.")
      handleFailure(inAppPayment.id, DonationError.UserCancelledPaymentError(DonationErrorSource.ONE_TIME))
      return Result.failure()
    }

    info("Setting default payment method.")
    StripeRepository.setDefaultPaymentMethod(
      paymentMethodId = statusAndPaymentMethodId.paymentMethod!!,
      setupIntentId = intentAccessor.intentId,
      subscriberType = inAppPayment.type.requireSubscriberType(),
      paymentSourceType = inAppPayment.data.paymentMethodType.toPaymentSourceType()
    )

    info("Setting subscription level.")
    RecurringInAppPaymentRepository.setSubscriptionLevelSync(inAppPayment)

    return Result.success()
  }

  override fun getFactoryKey(): String = KEY

  class Factory : Job.Factory<InAppPaymentStripeRecurringSetupJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InAppPaymentStripeRecurringSetupJob {
      val data = serializedData?.let { InAppPaymentSetupJobData.ADAPTER.decode(it) } ?: error("Missing job data!")

      return InAppPaymentStripeRecurringSetupJob(data, parameters)
    }
  }
}
