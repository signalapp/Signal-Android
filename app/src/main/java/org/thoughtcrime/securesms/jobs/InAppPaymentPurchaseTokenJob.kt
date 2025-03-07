/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import kotlinx.coroutines.runBlocking
import okio.IOException
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobManager.Chain
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.days

/**
 * Submits a purchase token to the server to link it with a subscriber id.
 */
class InAppPaymentPurchaseTokenJob private constructor(
  private val inAppPaymentId: InAppPaymentTable.InAppPaymentId,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(InAppPaymentPurchaseTokenJob::class)

    const val KEY = "InAppPaymentPurchaseTokenJob"

    private fun create(inAppPayment: InAppPaymentTable.InAppPayment): Job {
      return InAppPaymentPurchaseTokenJob(
        inAppPaymentId = inAppPayment.id,
        parameters = Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue(InAppPaymentsRepository.resolveJobQueueKey(inAppPayment))
          .setLifespan(3.days.inWholeMilliseconds)
          .setMaxAttempts(Parameters.UNLIMITED)
          .build()
      )
    }

    fun createJobChain(inAppPayment: InAppPaymentTable.InAppPayment): Chain {
      return AppDependencies.jobManager
        .startChain(create(inAppPayment))
        .then(InAppPaymentRecurringContextJob.create(inAppPayment))
        .then(InAppPaymentRedemptionJob.create(inAppPayment))
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

  override fun run(): Result {
    return InAppPaymentsRepository.resolveLock(inAppPaymentId).withLock {
      runBlocking { linkPurchaseToken() }
    }
  }

  private suspend fun linkPurchaseToken(): Result {
    if (!AppDependencies.billingApi.isApiAvailable()) {
      warning("Billing API is not available on this device. Exiting.")
      return Result.failure()
    }

    val purchase: BillingPurchaseResult = when (val purchase = AppDependencies.billingApi.queryPurchases()) {
      is BillingPurchaseResult.Success -> purchase
      else -> BillingPurchaseResult.None
    }

    if (purchase !is BillingPurchaseResult.Success || purchase.purchaseState != BillingPurchaseState.PURCHASED) {
      warning("Billing purchase not in the PURCHASED state. Retrying later.")
      return Result.retry(defaultBackoff())
    }

    val inAppPayment = try {
      getAndValidateInAppPayment()
    } catch (e: IOException) {
      warning("Failed to validate in-app payment.", e)
      return Result.failure()
    }

    info("Attempting to link purchase token for purchase")
    info("$purchase")

    val response = AppDependencies.donationsService.linkGooglePlayBillingPurchaseTokenToSubscriberId(
      inAppPayment.subscriberId!!,
      purchase.purchaseToken,
      InAppPaymentSubscriberRecord.Type.BACKUP.lock
    )

    if (response.applicationError.isPresent) {
      return handleApplicationError(response.applicationError.get(), response.status)
    } else if (response.result.isPresent) {
      info("Successfully linked purchase token to subscriber id.")
      return Result.success()
    } else {
      warning("Encountered a retryable exception.", response.executionError.get())
      return Result.retry(defaultBackoff())
    }
  }

  private fun getAndValidateInAppPayment(): InAppPaymentTable.InAppPayment {
    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    if (inAppPayment == null) {
      warning("Not found")
      throw IOException("InAppPayment for given ID not found.")
    }

    if (inAppPayment.state != InAppPaymentTable.State.PENDING) {
      warning("Unexpected state. Got ${inAppPayment.state} but expected PENDING")
      throw IOException("InAppPayment in unexpected state.")
    }

    if (inAppPayment.type != InAppPaymentType.RECURRING_BACKUP) {
      warning("Unexpected type. Got ${inAppPayment.type} but expected a recurring backup.")
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

    return inAppPayment
  }

  private fun handleApplicationError(applicationError: Throwable, status: Int): Result {
    return when (status) {
      402 -> {
        warning("The purchaseToken payment is incomplete or invalid.", applicationError)
        Result.retry(defaultBackoff())
      }

      403 -> {
        warning("subscriberId authentication failure OR account authentication is present", applicationError)
        Result.failure()
      }

      404 -> {
        warning("No such subscriberId exists or subscriberId is malformed or the purchaseToken does not exist", applicationError)
        Result.failure()
      }

      409 -> {
        warning("subscriberId is already linked to a processor that does not support Play Billing. Delete this subscriberId and use a new one.", applicationError)

        try {
          info("Generating a new subscriber id.")
          RecurringInAppPaymentRepository.ensureSubscriberId(InAppPaymentSubscriberRecord.Type.BACKUP, true).blockingAwait()

          info("Writing the new subscriber id to the InAppPayment.")
          val latest = SignalDatabase.inAppPayments.getById(inAppPaymentId)!!
          SignalDatabase.inAppPayments.update(
            latest.copy(subscriberId = InAppPaymentsRepository.requireSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP).subscriberId)
          )

          info("Scheduling retry.")
        } catch (e: Exception) {
          warning("Failed to generate and update subscriber id. Retrying later.", e)
        }

        Result.retry(defaultBackoff())
      }

      else -> {
        warning("An unknown error occurred.", applicationError)
        Result.failure()
      }
    }
  }

  private fun info(message: String, throwable: Throwable? = null) {
    Log.i(TAG, "InAppPayment[$inAppPaymentId]: $message", throwable, true)
  }

  private fun warning(message: String, throwable: Throwable? = null) {
    Log.w(TAG, "InAppPayment[$inAppPaymentId]: $message", throwable, true)
  }

  class Factory : Job.Factory<InAppPaymentPurchaseTokenJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InAppPaymentPurchaseTokenJob {
      return InAppPaymentPurchaseTokenJob(
        InAppPaymentTable.InAppPaymentId(serializedData!!.decodeToString().toLong()),
        parameters
      )
    }
  }
}
