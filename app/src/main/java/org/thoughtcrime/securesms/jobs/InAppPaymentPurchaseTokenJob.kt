/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import okio.IOException
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

/**
 * Submits a purchase token to the server to link it with a subscriber id.
 */
class InAppPaymentPurchaseTokenJob private constructor(
  private val inAppPaymentId: InAppPaymentTable.InAppPaymentId,
  parameters: Parameters
) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(InAppPaymentPurchaseTokenJob::class)

    const val KEY = "InAppPaymentPurchaseTokenJob"

    private fun create(inAppPayment: InAppPaymentTable.InAppPayment): Job {
      return InAppPaymentPurchaseTokenJob(
        inAppPaymentId = inAppPayment.id,
        parameters = Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue(InAppPaymentsRepository.resolveJobQueueKey(inAppPayment))
          .setLifespan(InAppPaymentsRepository.resolveContextJobLifespan(inAppPayment).inWholeMilliseconds)
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

  override fun onRun() {
    synchronized(InAppPaymentsRepository.resolveMutex(inAppPaymentId)) {
      doRun()
    }
  }

  private fun doRun() {
    val inAppPayment = getAndValidateInAppPayment()

    val response = AppDependencies.donationsService.linkGooglePlayBillingPurchaseTokenToSubscriberId(
      inAppPayment.subscriberId!!,
      inAppPayment.data.redemption!!.googlePlayBillingPurchaseToken!!,
      InAppPaymentSubscriberRecord.Type.BACKUP
    )

    if (response.applicationError.isPresent) {
      handleApplicationError(response.applicationError.get(), response.status)
    } else if (response.result.isPresent) {
      info("Successfully linked purchase token to subscriber id.")
    } else {
      warning("Encountered a retryable exception.", response.executionError.get())
      throw InAppPaymentRetryException(response.executionError.get())
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

    if (inAppPayment.data.redemption.googlePlayBillingPurchaseToken == null) {
      warning("No purchase token for linking!")
      throw IOException("InAppPayment does not have a purchase token!")
    }

    return inAppPayment
  }

  private fun handleApplicationError(applicationError: Throwable, status: Int) {
    when (status) {
      402 -> {
        warning("The purchaseToken payment is incomplete or invalid.", applicationError)
        // TODO [message-backups] -- Is this a recoverable failure?
        throw IOException("TODO -- recoverable?")
      }

      403 -> {
        warning("subscriberId authentication failure OR account authentication is present", applicationError)
        throw IOException("subscriberId authentication failure OR account authentication is present")
      }

      404 -> {
        warning("No such subscriberId exists or subscriberId is malformed or the purchaseToken does not exist", applicationError)
        throw IOException("No such subscriberId exists or subscriberId is malformed or the purchaseToken does not exist")
      }

      409 -> {
        warning("subscriberId is already linked to a processor that does not support Play Billing. Delete this subscriberId and use a new one.", applicationError)

        try {
          info("Generating a new subscriber id.")
          RecurringInAppPaymentRepository.ensureSubscriberId(InAppPaymentSubscriberRecord.Type.BACKUP, true).blockingAwait()
        } catch (e: Exception) {
          throw InAppPaymentRetryException(e)
        }

        info("Writing the new subscriber id to the InAppPayment.")
        val latest = SignalDatabase.inAppPayments.getById(inAppPaymentId)!!
        SignalDatabase.inAppPayments.update(
          latest.copy(subscriberId = InAppPaymentsRepository.requireSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP).subscriberId)
        )

        info("Scheduling retry.")
        throw InAppPaymentRetryException()
      }

      else -> {
        warning("An unknown error occurred.", applicationError)
        throw IOException(applicationError)
      }
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = e is InAppPaymentRetryException

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
