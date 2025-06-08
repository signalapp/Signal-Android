/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSource
import org.signal.donations.StripeApi
import org.signal.donations.StripeIntentAccessor
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.OneTimeInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.toPaymentSource
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.protos.InAppPaymentSetupJobData
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 *  Handles one-time Stripe transactions.
 */
class InAppPaymentStripeOneTimeSetupJob private constructor(
  data: InAppPaymentSetupJobData,
  parameters: Parameters
) : InAppPaymentSetupJob(data, parameters) {

  companion object {
    const val KEY = "InAppPaymentStripeOneTimeSetupJob"

    /**
     * Creates a new job for performing stripe recurring payment setup. Note that
     * we do not require network for this job, as if the network is not present, we
     * should treat that as an immediate error and fail the job.
     */
    fun create(
      inAppPayment: InAppPaymentTable.InAppPayment,
      paymentSource: PaymentSource
    ): InAppPaymentStripeOneTimeSetupJob {
      return InAppPaymentStripeOneTimeSetupJob(
        getJobData(inAppPayment, paymentSource),
        getParameters(inAppPayment)
      )
    }
  }

  override fun performPreUserAction(inAppPayment: InAppPaymentTable.InAppPayment): RequiredUserAction {
    info("Beginning one-time payment pipeline.")
    val amount = inAppPayment.data.amount!!.toFiatMoney()
    val recipientId = inAppPayment.data.recipientId?.let { RecipientId.from(it) } ?: Recipient.self().id
    if (inAppPayment.type == InAppPaymentType.ONE_TIME_GIFT) {
      info("Verifying recipient $recipientId can receive gift.")
      OneTimeInAppPaymentRepository.verifyRecipientIsAllowedToReceiveAGiftSync(recipientId)
    }

    info("Continuing payment...")
    val intentAccessor = StripeRepository.createPaymentIntent(amount, recipientId, inAppPayment.data.level, data.inAppPaymentSource!!.toPaymentSource().type)

    info("Confirming payment...")
    return when (val action = StripeRepository.confirmPaymentIntent(data.inAppPaymentSource.toPaymentSource(), intentAccessor, recipientId)) {
      is StripeApi.Secure3DSAction.ConfirmRequired -> RequiredUserAction.StripeActionRequired(action)
      is StripeApi.Secure3DSAction.NotNeeded -> RequiredUserAction.StripeActionNotRequired(action)
    }
  }

  override fun performPostUserAction(inAppPayment: InAppPaymentTable.InAppPayment): Result {
    val paymentMethodId = inAppPayment.data.stripeActionComplete!!.paymentMethodId
    val intentAccessor = StripeIntentAccessor(
      objectType = StripeIntentAccessor.ObjectType.PAYMENT_INTENT,
      intentId = inAppPayment.data.stripeActionComplete.stripeIntentId,
      intentClientSecret = inAppPayment.data.stripeActionComplete.stripeClientSecret
    )

    info("Getting status and payment method id from stripe.")
    val data = StripeRepository.getStatusAndPaymentMethodId(intentAccessor, paymentMethodId)

    if (!data.status.canProceed()) {
      warning("Cannot proceed with status ${data.status}.")
      handleFailure(inAppPayment.id, DonationError.UserCancelledPaymentError(DonationErrorSource.ONE_TIME))
      return Result.failure()
    }

    info("Received status and payment method id. Submitting redemption job chain.")
    OneTimeInAppPaymentRepository.submitRedemptionJobChain(inAppPayment, intentAccessor.intentId)

    return Result.success()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    return performTransaction()
  }

  class Factory : Job.Factory<InAppPaymentStripeOneTimeSetupJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InAppPaymentStripeOneTimeSetupJob {
      val data = serializedData?.let { InAppPaymentSetupJobData.ADAPTER.decode(it) } ?: error("Missing job data!")

      return InAppPaymentStripeOneTimeSetupJob(data, parameters)
    }
  }
}
