/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.donations.PaymentSource
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.toDonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.toProto
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.protos.InAppPaymentSetupJobData
import org.whispersystems.signalservice.internal.push.exceptions.InAppPaymentProcessorError

/**
 * Handles common pipeline logic between one-time and recurring transactions.
 */
abstract class InAppPaymentSetupJob(
  val data: InAppPaymentSetupJobData,
  parameters: Parameters
) : Job(parameters) {

  sealed interface RequiredUserAction {
    data class StripeActionNotRequired(val action: StripeApi.Secure3DSAction.NotNeeded) : RequiredUserAction
    data class StripeActionRequired(val action: StripeApi.Secure3DSAction.ConfirmRequired) : RequiredUserAction
    data class PayPalActionRequired(val approvalUrl: String, val tokenOrPaymentId: String) : RequiredUserAction
  }

  companion object {
    private val TAG = Log.tag(InAppPaymentSetupJob::class)

    @JvmStatic
    protected fun getParameters(inAppPayment: InAppPaymentTable.InAppPayment): Parameters {
      return Parameters.Builder()
        .setQueue(InAppPaymentsRepository.resolveJobQueueKey(inAppPayment))
        .setLifespan(InAppPaymentsRepository.resolveContextJobLifespanMillis(inAppPayment))
        .setMaxAttempts(Parameters.UNLIMITED)
        .build()
    }

    @JvmStatic
    protected fun getJobData(
      inAppPayment: InAppPaymentTable.InAppPayment,
      paymentSource: PaymentSource
    ): InAppPaymentSetupJobData {
      return InAppPaymentSetupJobData(
        inAppPaymentId = inAppPayment.id.rowId,
        inAppPaymentSource = paymentSource.toProto()
      )
    }
  }

  override fun serialize(): ByteArray = data.encode()

  /**
   * Given we have explicit exception handling, this is intentionally blank.
   */
  override fun onFailure() = Unit

  protected fun performTransaction(): Result {
    val inAppPaymentId = InAppPaymentTable.InAppPaymentId(data.inAppPaymentId)
    val inAppPayment = getAndValidateInAppPayment(inAppPaymentId)
    if (inAppPayment == null) {
      warning("No such payment, or payment was invalid. Failing.")
      return Result.failure()
    }

    if (data.inAppPaymentSource == null) {
      warning("No payment source attached to job. Failing.")
      handleFailure(inAppPaymentId, DonationError.getPaymentSetupError(inAppPayment.type.toErrorSource(), Exception(), inAppPayment.data.paymentMethodType.toPaymentSourceType()))
      return Result.failure()
    }

    if (inAppPayment.state == InAppPaymentTable.State.REQUIRED_ACTION_COMPLETED) {
      info("In REQUIRED_ACTION_COMPLETED state, performing post-3ds work.")

      info("Moving payment to TRANSACTING state.")
      val freshPayment = SignalDatabase.inAppPayments.moveToTransacting(inAppPaymentId)!!

      return try {
        performPostUserAction(freshPayment)
      } catch (e: Exception) {
        handleFailure(inAppPaymentId, e)
        Result.failure()
      }
    }

    try {
      info("Moving payment to TRANSACTING state.")
      val freshPayment = SignalDatabase.inAppPayments.moveToTransacting(inAppPaymentId)!!

      when (val action = performPreUserAction(freshPayment)) {
        is RequiredUserAction.StripeActionRequired -> {
          info("Stripe requires an action. Moving InAppPayment to REQUIRES_ACTION state.")

          val stripeSecure3DSAction = action.action
          val freshInAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)!!
          SignalDatabase.inAppPayments.update(
            inAppPayment = freshInAppPayment.copy(
              state = InAppPaymentTable.State.REQUIRES_ACTION,
              data = freshInAppPayment.data.newBuilder().stripeRequiresAction(
                stripeRequiresAction = InAppPaymentData.StripeRequiresActionState(
                  uri = stripeSecure3DSAction.uri.toString(),
                  returnUri = stripeSecure3DSAction.returnUri.toString(),
                  paymentMethodId = stripeSecure3DSAction.paymentMethodId.toString(),
                  stripeIntentId = stripeSecure3DSAction.stripeIntentAccessor.intentId,
                  stripeClientSecret = stripeSecure3DSAction.stripeIntentAccessor.intentClientSecret
                )
              ).build()
            )
          )

          return Result.failure()
        }

        is RequiredUserAction.StripeActionNotRequired -> {
          val iap = SignalDatabase.inAppPayments.getById(inAppPaymentId)!!
          val withCompletionData = iap.copy(
            data = iap.data.newBuilder().stripeActionComplete(
              stripeActionComplete = InAppPaymentData.StripeActionCompleteState(
                stripeIntentId = action.action.stripeIntentAccessor.intentId,
                stripeClientSecret = action.action.stripeIntentAccessor.intentClientSecret,
                paymentMethodId = action.action.paymentMethodId
              )
            ).build()
          )

          SignalDatabase.inAppPayments.update(withCompletionData)
          return performPostUserAction(
            inAppPayment = withCompletionData
          )
        }

        is RequiredUserAction.PayPalActionRequired -> {
          val freshInAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)!!
          SignalDatabase.inAppPayments.update(
            inAppPayment = freshInAppPayment.copy(
              state = InAppPaymentTable.State.REQUIRES_ACTION,
              data = freshInAppPayment.data.newBuilder().payPalRequiresAction(
                payPalRequiresAction = InAppPaymentData.PayPalRequiresActionState(
                  approvalUrl = action.approvalUrl,
                  token = action.tokenOrPaymentId
                )
              ).build()
            )
          )

          return Result.failure()
        }
      }
    } catch (e: Exception) {
      handleFailure(inAppPaymentId, e)
      return Result.failure()
    }
  }

  abstract fun performPreUserAction(inAppPayment: InAppPaymentTable.InAppPayment): RequiredUserAction

  abstract fun performPostUserAction(inAppPayment: InAppPaymentTable.InAppPayment): Result

  private fun getAndValidateInAppPayment(inAppPaymentId: InAppPaymentTable.InAppPaymentId): InAppPaymentTable.InAppPayment? {
    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)

    val isValid: Boolean = if (inAppPayment?.state == InAppPaymentTable.State.CREATED || inAppPayment?.state == InAppPaymentTable.State.REQUIRED_ACTION_COMPLETED) {
      val isStripeAndHasStripeCompletionData = inAppPayment.data.paymentMethodType.toPaymentSourceType() is PaymentSourceType.Stripe && inAppPayment.data.stripeActionComplete != null
      val isPayPalAndHAsPayPalCompletionData = inAppPayment.data.paymentMethodType.toPaymentSourceType() is PaymentSourceType.PayPal && inAppPayment.data.payPalActionComplete != null

      if (inAppPayment.state == InAppPaymentTable.State.REQUIRED_ACTION_COMPLETED && !isPayPalAndHAsPayPalCompletionData && !isStripeAndHasStripeCompletionData) {
        warning("Missing action data for REQUIRED_ACTION_COMPLETED state.")
        false
      } else {
        true
      }
    } else {
      warning("Missing or invalid in-app-payment in state ${inAppPayment?.state}")
      false
    }

    return if (!isValid) {
      if (inAppPayment != null) {
        handleFailure(
          inAppPaymentId,
          DonationError.getPaymentSetupError(
            source = inAppPayment.type.toErrorSource(),
            throwable = Exception(),
            method = inAppPayment.data.paymentMethodType.toPaymentSourceType()
          )
        )
      }
      null
    } else {
      inAppPayment
    }
  }

  protected fun handleFailure(inAppPaymentId: InAppPaymentTable.InAppPaymentId, exception: Exception) {
    warning("Failed to process transaction.", exception)

    val freshPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)!!

    val donationError: DonationError = when (exception) {
      is DonationError -> exception
      is InAppPaymentProcessorError -> exception.toDonationError(freshPayment.type.toErrorSource(), freshPayment.data.paymentMethodType.toPaymentSourceType())
      else -> DonationError.genericBadgeRedemptionFailure(freshPayment.type.toErrorSource())
    }

    SignalDatabase.inAppPayments.update(
      freshPayment.copy(
        notified = false,
        state = InAppPaymentTable.State.END,
        data = freshPayment.data.copy(
          error = InAppPaymentError.fromDonationError(donationError)?.inAppPaymentDataError
        )
      )
    )
  }

  protected fun info(message: String, throwable: Throwable? = null) {
    Log.i(TAG, "InAppPayment[${data.inAppPaymentId}]: $message", throwable, true)
  }

  protected fun warning(message: String, throwable: Throwable? = null) {
    Log.w(TAG, "InAppPayment[${data.inAppPaymentId}]: $message", throwable, true)
  }
}
