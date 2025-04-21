/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSource
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.OneTimeInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.PayPalRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal.PayPalConfirmationResult
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.protos.InAppPaymentSetupJobData
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentIntentResponse

class InAppPaymentPayPalOneTimeSetupJob private constructor(data: InAppPaymentSetupJobData, parameters: Parameters) : InAppPaymentSetupJob(data, parameters) {

  companion object {
    const val KEY = "InAppPaymentPayPalOneTimeSetupJob"

    /**
     * Creates a new job for performing stripe recurring payment setup. Note that
     * we do not require network for this job, as if the network is not present, we
     * should treat that as an immediate error and fail the job.
     */
    fun create(
      inAppPayment: InAppPaymentTable.InAppPayment,
      paymentSource: PaymentSource
    ): InAppPaymentPayPalOneTimeSetupJob {
      return InAppPaymentPayPalOneTimeSetupJob(
        getJobData(inAppPayment, paymentSource),
        getParameters(inAppPayment)
      )
    }
  }

  private val payPalRepository = PayPalRepository(AppDependencies.donationsService)

  override fun performPreUserAction(inAppPayment: InAppPaymentTable.InAppPayment): RequiredUserAction {
    info("Beginning one-time payment pipeline.")
    val amount = inAppPayment.data.amount!!.toFiatMoney()
    val recipientId = inAppPayment.data.recipientId?.let { RecipientId.from(it) } ?: Recipient.self().id
    if (inAppPayment.type == InAppPaymentType.ONE_TIME_GIFT) {
      info("Verifying recipient $recipientId can receive gift.")
      OneTimeInAppPaymentRepository.verifyRecipientIsAllowedToReceiveAGiftSync(recipientId)
    }

    info("Creating one-time payment intent...")
    val response: PayPalCreatePaymentIntentResponse = payPalRepository.createOneTimePaymentIntent(
      amount = amount,
      badgeRecipient = recipientId,
      badgeLevel = inAppPayment.data.level
    )

    return RequiredUserAction.PayPalActionRequired(
      approvalUrl = response.approvalUrl,
      tokenOrPaymentId = response.paymentId
    )
  }

  override fun performPostUserAction(inAppPayment: InAppPaymentTable.InAppPayment): Result {
    val result = PayPalConfirmationResult(
      payerId = inAppPayment.data.payPalActionComplete!!.payerId,
      paymentId = inAppPayment.data.payPalActionComplete.paymentId.takeIf { it.isNotBlank() },
      paymentToken = inAppPayment.data.payPalActionComplete.paymentToken
    )

    info("Confirming payment intent...")
    val response = payPalRepository.confirmOneTimePaymentIntent(
      amount = inAppPayment.data.amount!!.toFiatMoney(),
      badgeLevel = inAppPayment.data.level,
      paypalConfirmationResult = result
    )

    info("Confirmed payment intent. Submitting redemption job chain.")
    OneTimeInAppPaymentRepository.submitRedemptionJobChain(inAppPayment, response.paymentId)

    return Result.success()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    return performTransaction()
  }

  class Factory : Job.Factory<InAppPaymentPayPalOneTimeSetupJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InAppPaymentPayPalOneTimeSetupJob {
      val data = serializedData?.let { InAppPaymentSetupJobData.ADAPTER.decode(it) } ?: error("Missing job data!")

      return InAppPaymentPayPalOneTimeSetupJob(data, parameters)
    }
  }
}
