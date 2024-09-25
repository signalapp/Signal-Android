/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.receipts.ReceiptCredential
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequestContext
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialResponse
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toInAppPaymentDataChargeFailure
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobManager.Chain
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription.ChargeFailure
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription.Subscription
import org.whispersystems.signalservice.internal.ServiceResponse
import java.io.IOException
import java.util.Currency
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Given an inAppPaymentId, we want to take it's unconverted receipt context and
 * turn it into a receipt presentation.
 */
class InAppPaymentRecurringContextJob private constructor(
  private val inAppPaymentId: InAppPaymentTable.InAppPaymentId,
  parameters: Parameters
) : BaseJob(parameters) {
  companion object {
    private val TAG = Log.tag(InAppPaymentRecurringContextJob::class.java)

    const val KEY = "InAppPurchaseRecurringContextJob"

    fun create(inAppPayment: InAppPaymentTable.InAppPayment): Job {
      return InAppPaymentRecurringContextJob(
        inAppPaymentId = inAppPayment.id,
        parameters = Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue(InAppPaymentsRepository.resolveJobQueueKey(inAppPayment))
          .setLifespan(InAppPaymentsRepository.resolveContextJobLifespan(inAppPayment).inWholeMilliseconds)
          .setMaxAttempts(Parameters.UNLIMITED)
          .build()
      )
    }

    /**
     * Creates a job chain using data from the given InAppPayment. This object is passed by ID to the job,
     * meaning the job will always load the freshest data it can about the payment.
     */
    fun createJobChain(inAppPayment: InAppPaymentTable.InAppPayment, makePrimary: Boolean = false): Chain {
      return if (inAppPayment.type == InAppPaymentType.RECURRING_BACKUP) {
        AppDependencies.jobManager
          .startChain(create(inAppPayment))
          .then(InAppPaymentRedemptionJob.create(inAppPayment, makePrimary))
      } else {
        AppDependencies.jobManager
          .startChain(create(inAppPayment))
          .then(InAppPaymentRedemptionJob.create(inAppPayment, makePrimary))
          .then(RefreshOwnProfileJob())
          .then(MultiDeviceProfileContentUpdateJob())
      }
    }
  }

  override fun onAdded() {
    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    info("Added context job for payment with state ${inAppPayment?.state}")
    if (inAppPayment?.state == InAppPaymentTable.State.CREATED) {
      SignalDatabase.inAppPayments.update(
        inAppPayment.copy(
          state = InAppPaymentTable.State.PENDING
        )
      )
    }
  }

  override fun serialize(): ByteArray = inAppPaymentId.serialize().toByteArray()

  override fun getFactoryKey(): String = KEY

  override fun onFailure() {
    warning("A permanent failure occurred.")

    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    if (inAppPayment != null && inAppPayment.data.error == null) {
      SignalDatabase.inAppPayments.update(
        inAppPayment.copy(
          notified = false,
          state = InAppPaymentTable.State.END,
          data = inAppPayment.data.copy(
            error = InAppPaymentData.Error(
              type = InAppPaymentData.Error.Type.REDEMPTION
            )
          )
        )
      )
    }
  }

  override fun getNextRunAttemptBackoff(pastAttemptCount: Int, exception: java.lang.Exception): Long {
    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    return if (inAppPayment != null) {
      when (inAppPayment.data.paymentMethodType) {
        InAppPaymentData.PaymentMethodType.SEPA_DEBIT, InAppPaymentData.PaymentMethodType.IDEAL -> 1.days.inWholeMilliseconds
        else -> super.getNextRunAttemptBackoff(pastAttemptCount, exception)
      }
    } else {
      super.getNextRunAttemptBackoff(pastAttemptCount, exception)
    }
  }

  override fun onRun() {
    synchronized(InAppPaymentsRepository.resolveMutex(inAppPaymentId)) {
      doRun()
    }
  }

  private fun doRun() {
    val (inAppPayment, requestContext) = getAndValidateInAppPayment()
    val activeSubscription = getActiveSubscription(inAppPayment)
    val subscription = activeSubscription.activeSubscription

    if (subscription == null) {
      warning("Subscription is null. Retrying later.")
      throw InAppPaymentRetryException()
    }

    handlePossibleFailedPayment(inAppPayment, activeSubscription, subscription)
    handlePossibleInactiveSubscription(inAppPayment, activeSubscription, subscription)

    info("Subscription is valid, proceeding with request for ReceiptCredentialResponse")

    val updatedInAppPayment: InAppPaymentTable.InAppPayment = if (inAppPayment.data.redemption!!.stage != InAppPaymentData.RedemptionState.Stage.CONVERSION_STARTED || inAppPayment.endOfPeriod.inWholeMilliseconds <= 0) {
      info("Updating payment state with endOfCurrentPeriod and proper stage.")

      if (inAppPayment.type.requireSubscriberType() == InAppPaymentSubscriberRecord.Type.DONATION) {
        info("Recording last end of period.")
        SignalStore.inAppPayments.setLastEndOfPeriod(subscription.endOfCurrentPeriod)
      }

      SignalDatabase.inAppPayments.update(
        inAppPayment.copy(
          endOfPeriod = subscription.endOfCurrentPeriod.seconds,
          data = inAppPayment.data.copy(
            redemption = inAppPayment.data.redemption.copy(
              stage = InAppPaymentData.RedemptionState.Stage.CONVERSION_STARTED
            )
          )
        )
      )

      requireNotNull(SignalDatabase.inAppPayments.getById(inAppPaymentId))
    } else {
      inAppPayment
    }

    submitAndValidateCredentials(updatedInAppPayment, subscription, requestContext)
  }

  private fun getAndValidateInAppPayment(): Pair<InAppPaymentTable.InAppPayment, ReceiptCredentialRequestContext> {
    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    if (inAppPayment == null) {
      warning("Not found")
      throw IOException("InAppPayment for given ID not found.")
    }

    if (inAppPayment.state != InAppPaymentTable.State.PENDING) {
      warning("Unexpected state. Got ${inAppPayment.state} but expected PENDING")
      throw IOException("InAppPayment in unexpected state.")
    }

    if (!inAppPayment.type.recurring) {
      warning("Unexpected type. Got ${inAppPayment.type} but expected a recurring type.")
      throw IOException("InAppPayment is an unexpected type.")
    }

    if (inAppPayment.subscriberId == null) {
      warning("Expected a subscriber id.")
      throw IOException("InAppPayment is missing its subscriber id")
    }

    if (inAppPayment.data.redemption == null) {
      warning("Expected redemption state.")
      throw IOException("InAppPayment has no redemption state. Waiting for authorization?")
    }

    if (inAppPayment.data.redemption.stage == InAppPaymentData.RedemptionState.Stage.REDEMPTION_STARTED || inAppPayment.data.redemption.stage == InAppPaymentData.RedemptionState.Stage.REDEEMED) {
      warning("Already began redemption.")
      throw IOException("InAppPayment has already started redemption.")
    }

    return if (inAppPayment.data.redemption.receiptCredentialRequestContext == null) {
      val requestContext = InAppPaymentsRepository.generateRequestCredential()
      val updatedPayment = inAppPayment.copy(
        data = inAppPayment.data.copy(
          redemption = inAppPayment.data.redemption.copy(
            stage = InAppPaymentData.RedemptionState.Stage.CONVERSION_STARTED,
            receiptCredentialRequestContext = requestContext.serialize().toByteString()
          )
        )
      )

      SignalDatabase.inAppPayments.update(updatedPayment)
      updatedPayment to requestContext
    } else {
      inAppPayment to ReceiptCredentialRequestContext(inAppPayment.data.redemption.receiptCredentialRequestContext.toByteArray())
    }
  }

  private fun getActiveSubscription(inAppPayment: InAppPaymentTable.InAppPayment): ActiveSubscription {
    val activeSubscriptionResponse = AppDependencies.donationsService.getSubscription(inAppPayment.subscriberId)
    return if (activeSubscriptionResponse.result.isPresent) {
      activeSubscriptionResponse.result.get()
    } else if (activeSubscriptionResponse.applicationError.isPresent) {
      warning("An application error occurred while trying to get the active subscription. Failing.", activeSubscriptionResponse.applicationError.get())
      throw IOException(activeSubscriptionResponse.applicationError.get())
    } else {
      warning("An execution error occurred. Retrying later.", activeSubscriptionResponse.executionError.get())
      throw InAppPaymentRetryException(activeSubscriptionResponse.executionError.get())
    }
  }

  private fun handlePossibleFailedPayment(
    inAppPayment: InAppPaymentTable.InAppPayment,
    activeSubscription: ActiveSubscription,
    subscription: Subscription
  ) {
    if (subscription.isFailedPayment) {
      val chargeFailure = activeSubscription.chargeFailure
      if (chargeFailure != null) {
        warning("Charge failure detected on active subscription: ${chargeFailure.code}: ${chargeFailure.message}")
      }

      if (inAppPayment.data.redemption!!.keepAlive == true && !subscription.isCanceled) {
        warning("Payment failure for uncanceled subscription during keep-alive, allow keep-alive to retry later.")

        SignalDatabase.inAppPayments.update(
          inAppPayment.copy(
            notified = true,
            data = inAppPayment.data.copy(
              error = InAppPaymentData.Error(
                type = InAppPaymentData.Error.Type.PAYMENT_PROCESSING,
                data_ = InAppPaymentKeepAliveJob.KEEP_ALIVE
              )
            )
          )
        )

        throw Exception("Payment renewal is in keep-alive state, let keep-alive process retry later.")
      } else {
        handlePaymentFailure(inAppPayment, subscription, chargeFailure)
        throw Exception("New subscription has a payment failure: ${subscription.status}")
      }
    }
  }

  private fun handlePossibleInactiveSubscription(
    inAppPayment: InAppPaymentTable.InAppPayment,
    activeSubscription: ActiveSubscription,
    subscription: Subscription
  ) {
    val isForKeepAlive = inAppPayment.data.redemption!!.keepAlive == true
    if (!subscription.isActive) {
      val chargeFailure = activeSubscription.chargeFailure
      if (chargeFailure != null) {
        warning("Subscription payment charge failure code: ${chargeFailure.code}, message: ${chargeFailure.message}")

        if (!isForKeepAlive) {
          warning("Initial subscription payment failed, treating as a permanent failure.")
          handlePaymentFailure(inAppPayment, subscription, chargeFailure)
          throw Exception("New subscription has hit a payment failure.")
        }
      }

      if (isForKeepAlive && subscription.isCanceled) {
        warning("Permanent payment failure in renewing subscription. Status: ${subscription.status}")
        handlePaymentFailure(inAppPayment, subscription, chargeFailure)
        throw Exception()
      }

      warning("Subscription is not yet active. Status: ${subscription.status}")
      throw InAppPaymentRetryException()
    }
  }

  private fun handlePaymentFailure(inAppPayment: InAppPaymentTable.InAppPayment, subscription: Subscription, chargeFailure: ChargeFailure?) {
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = inAppPayment.subscriberId!!,
        currency = Currency.getInstance(inAppPayment.data.amount!!.currencyCode),
        type = inAppPayment.type.requireSubscriberType(),
        requiresCancel = true,
        paymentMethodType = inAppPayment.data.paymentMethodType
      )
    )

    if (inAppPayment.data.redemption?.keepAlive == true) {
      info("Cancellation occurred during keep-alive. Setting cancellation state.")

      SignalDatabase.inAppPayments.update(
        inAppPayment.copy(
          notified = false,
          state = InAppPaymentTable.State.END,
          data = inAppPayment.data.copy(
            cancellation = InAppPaymentData.Cancellation(
              reason = when (subscription.status) {
                "past_due" -> InAppPaymentData.Cancellation.Reason.PAST_DUE
                "canceled" -> InAppPaymentData.Cancellation.Reason.CANCELED
                "unpaid" -> InAppPaymentData.Cancellation.Reason.UNPAID
                else -> InAppPaymentData.Cancellation.Reason.UNKNOWN
              },
              chargeFailure = chargeFailure?.toInAppPaymentDataChargeFailure()
            )
          )
        )
      )

      MultiDeviceSubscriptionSyncRequestJob.enqueue()
    } else if (chargeFailure != null) {
      info("Charge failure detected: $chargeFailure")

      SignalDatabase.inAppPayments.update(
        inAppPayment.copy(
          notified = false,
          state = InAppPaymentTable.State.END,
          data = inAppPayment.data.copy(
            error = InAppPaymentsRepository.buildPaymentFailure(inAppPayment, chargeFailure)
          )
        )
      )
    } else {
      info("Generic payment failure detected.")

      SignalDatabase.inAppPayments.update(
        inAppPayment.copy(
          notified = false,
          state = InAppPaymentTable.State.END,
          data = inAppPayment.data.copy(
            error = InAppPaymentData.Error(
              type = InAppPaymentData.Error.Type.PAYMENT_PROCESSING,
              data_ = subscription.status
            )
          )
        )
      )
    }
  }

  private fun submitAndValidateCredentials(
    inAppPayment: InAppPaymentTable.InAppPayment,
    subscription: Subscription,
    requestContext: ReceiptCredentialRequestContext
  ) {
    info("Submitting receipt credential request")
    val response: ServiceResponse<ReceiptCredentialResponse> = AppDependencies.donationsService.submitReceiptCredentialRequestSync(inAppPayment.subscriberId!!, requestContext.request)

    if (response.applicationError.isPresent) {
      handleApplicationError(inAppPayment, response)
    } else if (response.result.isPresent) {
      handleResult(inAppPayment, subscription, requestContext, response.result.get())
    } else {
      warning("Encountered a retryable exception.", response.executionError.get())
      throw InAppPaymentRetryException(response.executionError.get())
    }
  }

  private fun handleApplicationError(
    inAppPayment: InAppPaymentTable.InAppPayment,
    serviceResponse: ServiceResponse<ReceiptCredentialResponse>
  ) {
    val isForKeepAlive = inAppPayment.data.redemption!!.keepAlive == true
    val applicationError = serviceResponse.applicationError.get()
    when (serviceResponse.status) {
      204 -> {
        warning("Payment is still processing. Try again later.", applicationError)
        throw InAppPaymentRetryException(applicationError)
      }

      400 -> {
        warning("Receipt credential request failed to validate.", applicationError)
        updateInAppPaymentWithGenericRedemptionError(inAppPayment)
        throw Exception(applicationError)
      }

      402 -> {
        warning("Payment looks like a failure but may be retried", applicationError)
        throw InAppPaymentRetryException(applicationError)
      }

      403 -> {
        warning("SubscriberId password mismatch or account auth was present", applicationError)
        updateInAppPaymentWithGenericRedemptionError(inAppPayment)
        throw Exception(applicationError)
      }

      404 -> {
        warning("SubscriberId not found or malformed.", applicationError)
        updateInAppPaymentWithGenericRedemptionError(inAppPayment)
        throw Exception(applicationError)
      }

      409 -> {
        if (isForKeepAlive) {
          warning("Already redeemed this token during keep-alive, ignoring.", applicationError)
          SignalDatabase.inAppPayments.update(
            inAppPayment.copy(
              state = InAppPaymentTable.State.END,
              data = inAppPayment.data.copy(redemption = inAppPayment.data.redemption.copy(stage = InAppPaymentData.RedemptionState.Stage.REDEEMED))
            )
          )
        } else {
          warning("Already redeemed this token during new subscription. Failing.", applicationError)
          updateInAppPaymentWithGenericRedemptionError(inAppPayment)
        }
      }

      else -> {
        warning("Encountered a server error.", applicationError)

        throw InAppPaymentRetryException(applicationError)
      }
    }
  }

  private fun handleResult(
    inAppPayment: InAppPaymentTable.InAppPayment,
    subscription: Subscription,
    requestContext: ReceiptCredentialRequestContext,
    response: ReceiptCredentialResponse
  ) {
    val operations = AppDependencies.clientZkReceiptOperations
    val receiptCredential: ReceiptCredential = try {
      operations.receiveReceiptCredential(requestContext, response)
    } catch (e: VerificationFailedException) {
      warning("Encountered an exception when receiving receipt credential from zk.", e)
      throw InAppPaymentRetryException(e)
    }

    if (!isCredentialValid(subscription, receiptCredential)) {
      updateInAppPaymentWithGenericRedemptionError(inAppPayment)
      throw IOException("Could not validate receipt credential")
    }

    val receiptCredentialPresentation: ReceiptCredentialPresentation = try {
      operations.createReceiptCredentialPresentation(receiptCredential)
    } catch (e: VerificationFailedException) {
      warning("Encountered an exception when creating receipt credential presentation via zk.", e)
      throw InAppPaymentRetryException(e)
    }

    info("Validated credential. Recording receipt and handing off to redemption job.")
    SignalDatabase.donationReceipts.addReceipt(InAppPaymentReceiptRecord.createForSubscription(subscription))
    SignalDatabase.inAppPayments.update(
      inAppPayment = inAppPayment.copy(
        data = inAppPayment.data.copy(
          redemption = inAppPayment.data.redemption!!.copy(
            stage = InAppPaymentData.RedemptionState.Stage.REDEMPTION_STARTED,
            receiptCredentialPresentation = receiptCredentialPresentation.serialize().toByteString()
          )
        )
      )
    )
  }

  private fun isCredentialValid(subscription: Subscription, receiptCredential: ReceiptCredential): Boolean {
    val now = System.currentTimeMillis().milliseconds
    val maxExpirationTime = now + 90.days
    val isSameLevel = subscription.level.toLong() == receiptCredential.receiptLevel
    val isExpirationAfterSub = subscription.endOfCurrentPeriod < receiptCredential.receiptExpirationTime
    val isExpiration86400 = receiptCredential.receiptExpirationTime % 86400L == 0L
    val isExpirationInTheFuture = receiptCredential.receiptExpirationTime.seconds > now
    val isExpirationWithinMax = receiptCredential.receiptExpirationTime.seconds <= maxExpirationTime

    info(
      """
      Credential Validation
      isSameLevel $isSameLevel
      isExpirationAfterSub $isExpirationAfterSub
      isExpiration86400 $isExpiration86400
      isExpirationInTheFuture $isExpirationInTheFuture
      isExpirationWithinMax $isExpirationWithinMax
      """.trimIndent()
    )

    return isSameLevel && isExpirationAfterSub && isExpiration86400 && isExpirationInTheFuture && isExpirationWithinMax
  }

  private fun updateInAppPaymentWithGenericRedemptionError(inAppPayment: InAppPaymentTable.InAppPayment) {
    SignalDatabase.inAppPayments.update(
      inAppPayment = inAppPayment.copy(
        state = InAppPaymentTable.State.END,
        data = inAppPayment.data.copy(
          error = InAppPaymentData.Error(
            type = InAppPaymentData.Error.Type.REDEMPTION
          )
        )
      )
    )
  }

  override fun onShouldRetry(e: Exception): Boolean = e is InAppPaymentRetryException

  private fun info(message: String, throwable: Throwable? = null) {
    Log.i(TAG, "InAppPayment[$inAppPaymentId]: $message", throwable, true)
  }

  private fun warning(message: String, throwable: Throwable? = null) {
    Log.w(TAG, "InAppPayment[$inAppPaymentId]: $message", throwable, true)
  }

  class Factory : Job.Factory<InAppPaymentRecurringContextJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InAppPaymentRecurringContextJob {
      return InAppPaymentRecurringContextJob(
        InAppPaymentTable.InAppPaymentId(serializedData!!.decodeToString().toLong()),
        parameters
      )
    }
  }
}
