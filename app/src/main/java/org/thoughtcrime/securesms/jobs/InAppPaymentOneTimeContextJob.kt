/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.donations.InAppPaymentType
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.receipts.ReceiptCredential
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequestContext
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toDonationProcessor
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobManager.Chain
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.exceptions.DonationReceiptCredentialError
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Handles processing and validation of one-time payments. This involves
 * generating and submitting a receipt request context to the server and
 * processing its returned parameters.
 */
class InAppPaymentOneTimeContextJob private constructor(
  private val inAppPaymentId: InAppPaymentTable.InAppPaymentId,
  parameters: Parameters
) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(InAppPaymentOneTimeContextJob::class.java)

    const val KEY = "InAppPurchaseOneTimeContextJob"

    private fun create(inAppPayment: InAppPaymentTable.InAppPayment): Job {
      return InAppPaymentOneTimeContextJob(
        inAppPayment.id,
        parameters = Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue(InAppPaymentsRepository.resolveJobQueueKey(inAppPayment))
          .setLifespan(InAppPaymentsRepository.resolveContextJobLifespan(inAppPayment).inWholeMilliseconds)
          .setMaxAttempts(Parameters.UNLIMITED)
          .build()
      )
    }

    fun createJobChain(inAppPayment: InAppPaymentTable.InAppPayment, makePrimary: Boolean = false): Chain {
      return when (inAppPayment.type) {
        InAppPaymentType.ONE_TIME_DONATION -> {
          AppDependencies.jobManager
            .startChain(create(inAppPayment))
            .then(InAppPaymentRedemptionJob.create(inAppPayment, makePrimary))
            .then(RefreshOwnProfileJob())
            .then(MultiDeviceProfileContentUpdateJob())
        }
        InAppPaymentType.ONE_TIME_GIFT -> {
          AppDependencies.jobManager
            .startChain(create(inAppPayment))
            .then(InAppPaymentGiftSendJob.create(inAppPayment))
        }
        else -> error("Unsupported type: ${inAppPayment.type}")
      }
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

  override fun onAdded() {
    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    if (inAppPayment?.state == InAppPaymentTable.State.CREATED) {
      SignalDatabase.inAppPayments.update(
        inAppPayment.copy(
          state = InAppPaymentTable.State.PENDING
        )
      )
    }
  }

  override fun onRun() {
    val (inAppPayment, requestContext) = getAndValidateInAppPayment()

    info("Submitting request context to server...")
    val serviceResponse = AppDependencies.donationsService.submitBoostReceiptCredentialRequestSync(
      inAppPayment.data.redemption!!.paymentIntentId,
      requestContext.request,
      inAppPayment.data.paymentMethodType.toDonationProcessor()
    )

    if (serviceResponse.applicationError.isPresent) {
      handleApplicationError(inAppPayment, serviceResponse)
    } else if (serviceResponse.result.isPresent) {
      val receiptCredential = try {
        AppDependencies.clientZkReceiptOperations.receiveReceiptCredential(requestContext, serviceResponse.result.get())
      } catch (e: VerificationFailedException) {
        warning("Failed to receive credential.", e)
        throw InAppPaymentRetryException(e)
      }

      if (isCredentialValid(inAppPayment, receiptCredential)) {
        info("Validated credential. Getting presentation.")
        val receiptCredentialPresentation = try {
          AppDependencies.clientZkReceiptOperations.createReceiptCredentialPresentation(receiptCredential)
        } catch (e: VerificationFailedException) {
          warning("Failed to get presentation from credential.")
          throw InAppPaymentRetryException(e)
        }

        info("Got presentation. Updating state and completing.")
        SignalDatabase.inAppPayments.update(
          inAppPayment.copy(
            data = inAppPayment.data.copy(
              redemption = InAppPaymentData.RedemptionState(
                stage = InAppPaymentData.RedemptionState.Stage.REDEMPTION_STARTED,
                receiptCredentialPresentation = receiptCredentialPresentation.serialize().toByteString()
              )
            )
          )
        )
      } else {
        warning("Failed to validate credential.")
        SignalDatabase.inAppPayments.update(
          inAppPayment.copy(
            notified = false,
            state = InAppPaymentTable.State.END,
            data = inAppPayment.data.copy(
              error = InAppPaymentData.Error(
                type = InAppPaymentData.Error.Type.CREDENTIAL_VALIDATION
              )
            )
          )
        )
        throw IOException("Could not validate credential.")
      }
    } else {
      info("Encountered a retryable error", serviceResponse.executionError.orNull())
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = e is InAppPaymentRetryException

  private fun getAndValidateInAppPayment(): Pair<InAppPaymentTable.InAppPayment, ReceiptCredentialRequestContext> {
    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    if (inAppPayment == null) {
      warning("Not found in database.")
      throw IOException("InAppPayment not found in database")
    }

    if (inAppPayment.type.recurring) {
      warning("Invalid type: ${inAppPayment.type}")
      throw IOException("InAppPayment is of unexpected type")
    }

    if (inAppPayment.state != InAppPaymentTable.State.PENDING) {
      warning("Invalid state: ${inAppPayment.state} but expected PENDING")
      throw IOException("InAppPayment is in an invalid state")
    }

    if (inAppPayment.data.redemption == null) {
      warning("Invalid data: not in redemption state.")
      throw IOException("InAppPayment does not have a redemption state. Still awaiting auth?")
    }

    if (inAppPayment.data.redemption.stage != InAppPaymentData.RedemptionState.Stage.INIT && inAppPayment.data.redemption.stage != InAppPaymentData.RedemptionState.Stage.CONVERSION_STARTED) {
      warning("Invalid stage: Expected INIT or CONVERSION_STARTED, but got ${inAppPayment.data.redemption.stage}")
      throw IOException("InAppPayment is in an invalid stage.")
    }

    if (inAppPayment.data.redemption.paymentIntentId == null) {
      warning("No payment id present on one-time redemption data. Exiting.")
      throw IOException("InAppPayment has no paymentIntentId.")
    }

    val requestContext: ReceiptCredentialRequestContext = inAppPayment.data.redemption.receiptCredentialRequestContext?.let {
      ReceiptCredentialRequestContext(it.toByteArray())
    } ?: InAppPaymentsRepository.generateRequestCredential()

    val updatedPayment = inAppPayment.copy(
      data = inAppPayment.data.copy(
        redemption = inAppPayment.data.redemption.copy(
          stage = InAppPaymentData.RedemptionState.Stage.CONVERSION_STARTED,
          receiptCredentialRequestContext = requestContext.serialize().toByteString()
        )
      )
    )

    SignalDatabase.inAppPayments.update(updatedPayment)
    return updatedPayment to requestContext
  }

  private fun <T> handleApplicationError(inAppPayment: InAppPaymentTable.InAppPayment, serviceResponse: ServiceResponse<T>) {
    val applicationError = serviceResponse.applicationError.get()
    when (serviceResponse.status) {
      204 -> {
        warning("Payment may not be completed yet. Retry later.", applicationError)
        throw InAppPaymentRetryException(applicationError)
      }

      400 -> {
        warning("Receipt credential failed to validate.", applicationError)
      }

      402 -> {
        warning("Payment has failed", applicationError)
        SignalDatabase.inAppPayments.update(
          inAppPayment.copy(
            notified = false,
            state = InAppPaymentTable.State.END,
            data = inAppPayment.data.copy(
              error = InAppPaymentsRepository.buildPaymentFailure(inAppPayment, (applicationError as? DonationReceiptCredentialError)?.chargeFailure)
            )
          )
        )

        throw IOException(applicationError)
      }

      409 -> {
        warning("Receipt already redeemed with a different request credential", applicationError)
        SignalDatabase.inAppPayments.update(
          inAppPayment.copy(
            notified = false,
            state = InAppPaymentTable.State.END,
            data = inAppPayment.data.copy(
              error = InAppPaymentData.Error(
                type = InAppPaymentData.Error.Type.REDEMPTION,
                data_ = "409"
              )
            )
          )
        )

        throw IOException(applicationError)
      }

      else -> {
        warning("Encountered a server failure. Retry later", applicationError)
        throw InAppPaymentRetryException(applicationError)
      }
    }
  }

  private fun isCredentialValid(inAppPayment: InAppPaymentTable.InAppPayment, receiptCredential: ReceiptCredential): Boolean {
    val now = System.currentTimeMillis().milliseconds
    val maxExpirationTime = now + 90.days
    val isCorrectLevel = receiptCredential.receiptLevel == inAppPayment.data.level
    val isExpiration86400 = receiptCredential.receiptExpirationTime % 86400 == 0L
    val isExpirationInTheFuture = receiptCredential.receiptExpirationTime.seconds > now
    val isExpirationWithinMax = receiptCredential.receiptExpirationTime.seconds <= maxExpirationTime

    info(
      """
      Credential Validation
      -
      isCorrectLevel $isCorrectLevel actual: ${receiptCredential.receiptLevel} expected: ${inAppPayment.data.level}
      isExpiration86400 $isExpiration86400
      isExpirationInTheFuture $isExpirationInTheFuture
      isExpirationWithinMax $isExpirationWithinMax
      """.trimIndent()
    )

    return isCorrectLevel && isExpiration86400 && isExpirationInTheFuture && isExpirationWithinMax
  }

  private fun info(message: String, throwable: Throwable? = null) {
    Log.i(TAG, "InAppPayment[$inAppPaymentId]: $message", throwable, true)
  }

  private fun warning(message: String, throwable: Throwable? = null) {
    Log.w(TAG, "InAppPayment[$inAppPaymentId]: $message", throwable, true)
  }

  class Factory : Job.Factory<InAppPaymentOneTimeContextJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InAppPaymentOneTimeContextJob {
      return InAppPaymentOneTimeContextJob(
        InAppPaymentTable.InAppPaymentId(serializedData!!.decodeToString().toLong()),
        parameters
      )
    }
  }
}
