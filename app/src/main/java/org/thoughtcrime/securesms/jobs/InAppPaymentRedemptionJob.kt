/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.InAppPaymentRedemptionJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.requireGiftBadge
import org.whispersystems.signalservice.internal.ServiceResponse
import java.io.IOException

/**
 * Takes a ReceiptCredentialResponse and submits it to the server for redemption.
 *
 * Redemption is fairly straight forward:
 * 1. Verify we have all the peices of data we need and haven't already redeemed the token
 * 2. Attempt to redeem the token
 * 3. Either mark down the error or the success
 */
class InAppPaymentRedemptionJob private constructor(
  private val jobData: InAppPaymentRedemptionJobData,
  parameters: Parameters
) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(InAppPaymentRedemptionJob::class.java)
    const val KEY = "InAppPurchaseRedemptionJob"

    private const val MAX_RETRIES = 1500

    /**
     * Utilized when active subscription is in the REDEMPTION_STARTED stage of the redemption pipeline
     */
    fun enqueueJobChainForRecurringKeepAlive(inAppPayment: InAppPaymentTable.InAppPayment) {
      AppDependencies.jobManager
        .startChain(create(inAppPayment, makePrimary = false))
        .then(RefreshOwnProfileJob())
        .then(MultiDeviceProfileContentUpdateJob())
        .enqueue()
    }

    fun create(
      inAppPayment: InAppPaymentTable.InAppPayment? = null,
      makePrimary: Boolean = false
    ): Job {
      return create(
        inAppPayment = inAppPayment,
        giftMessageId = null,
        makePrimary = makePrimary
      )
    }

    fun create(
      giftMessageId: MessageId,
      makePrimary: Boolean
    ): Job {
      return create(
        inAppPayment = null,
        giftMessageId = giftMessageId,
        makePrimary = makePrimary
      )
    }

    private fun create(
      inAppPayment: InAppPaymentTable.InAppPayment? = null,
      makePrimary: Boolean = false,
      giftMessageId: MessageId? = null
    ): Job {
      return InAppPaymentRedemptionJob(
        jobData = InAppPaymentRedemptionJobData(
          inAppPaymentId = inAppPayment?.id?.rowId,
          giftMessageId = giftMessageId?.id,
          makePrimary = makePrimary
        ),
        parameters = Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue(inAppPayment?.let { InAppPaymentsRepository.resolveJobQueueKey(it) } ?: "InAppGiftReceiptRedemption-$giftMessageId")
          .setMaxAttempts(MAX_RETRIES)
          .setLifespan(Parameters.IMMORTAL)
          .build()
      )
    }
  }

  override fun serialize(): ByteArray = jobData.encode()

  override fun getFactoryKey(): String = KEY

  override fun onAdded() {
    if (jobData.giftMessageId != null) {
      Log.d(TAG, "GiftMessage with ID ${jobData.giftMessageId} will be marked as started")
      SignalDatabase.messages.markGiftRedemptionStarted(jobData.giftMessageId)
    }
  }

  override fun onFailure() {
    if (jobData.giftMessageId != null) {
      Log.d(TAG, "GiftMessage with ID ${jobData.giftMessageId} will be marked as a failure")
      SignalDatabase.messages.markGiftRedemptionFailed(jobData.giftMessageId)
    }

    if (jobData.inAppPaymentId != null) {
      Log.w(TAG, "A permanent failure occurred.")

      val inAppPayment = SignalDatabase.inAppPayments.getById(InAppPaymentTable.InAppPaymentId(jobData.inAppPaymentId))
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
  }

  override fun onRun() {
    if (jobData.inAppPaymentId != null) {
      onRunForInAppPayment(InAppPaymentTable.InAppPaymentId(jobData.inAppPaymentId))
    } else {
      onRunForGiftMessageId(jobData.giftMessageId!!)
    }
  }

  private fun onRunForGiftMessageId(messageId: Long) {
    val giftBadge = SignalDatabase.messages.getMessageRecord(messageId)
    if (!giftBadge.hasGiftBadge()) {
      Log.w(TAG, "GiftMessage with ID $messageId not found. Failing.", true)
      return
    }

    if (giftBadge.requireGiftBadge().redemptionState == GiftBadge.RedemptionState.REDEEMED) {
      Log.w(TAG, "GiftMessage with ID $messageId has already been redeemed. Exiting.", true)
    }

    val credentialBytes = giftBadge.requireGiftBadge().redemptionToken.toByteArray()
    val receiptCredentialPresentation = ReceiptCredentialPresentation(credentialBytes)

    Log.d(TAG, "Attempting to redeem receipt credential presentation...", true)
    val serviceResponse = AppDependencies
      .donationsService
      .redeemDonationReceipt(
        receiptCredentialPresentation,
        SignalStore.inAppPayments.getDisplayBadgesOnProfile(),
        jobData.makePrimary
      )

    verifyServiceResponse(serviceResponse)

    Log.d(TAG, "GiftMessage with ID $messageId has been redeemed.")
    SignalDatabase.messages.markGiftRedemptionCompleted(messageId)
  }

  private fun onRunForInAppPayment(inAppPaymentId: InAppPaymentTable.InAppPaymentId) {
    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    if (inAppPayment == null) {
      Log.w(TAG, "InAppPayment with ID $inAppPaymentId not found. Failing.", true)
      return
    }

    if (inAppPayment.type.recurring) {
      synchronized(inAppPayment.type.requireSubscriberType()) {
        performInAppPaymentRedemption(inAppPayment)
      }
    } else {
      performInAppPaymentRedemption(inAppPayment)
    }
  }

  private fun performInAppPaymentRedemption(inAppPayment: InAppPaymentTable.InAppPayment) {
    val inAppPaymentId = inAppPayment.id
    if (inAppPayment.state != InAppPaymentTable.State.PENDING) {
      Log.w(TAG, "InAppPayment with ID $inAppPaymentId is in state ${inAppPayment.state}, expected PENDING. Exiting.", true)
      return
    }

    if (inAppPayment.data.redemption == null || inAppPayment.data.redemption.stage != InAppPaymentData.RedemptionState.Stage.REDEMPTION_STARTED) {
      Log.w(TAG, "Recurring InAppPayment with ID $inAppPaymentId is in stage ${inAppPayment.data.redemption?.stage}. Expected REDEMPTION_STARTED. Exiting.", true)
      return
    }

    val credentialBytes = inAppPayment.data.redemption.receiptCredentialPresentation
    if (credentialBytes == null) {
      Log.w(TAG, "InAppPayment with ID $inAppPaymentId does not have a receipt credential presentation. Nothing to redeem. Exiting.", true)
      return
    }

    val receiptCredentialPresentation = ReceiptCredentialPresentation(credentialBytes.toByteArray())

    val serviceResponse = if (inAppPayment.type == InAppPaymentType.RECURRING_BACKUP) {
      Log.d(TAG, "Attempting to redeem archive receipt credential presentation...", true)
      AppDependencies
        .donationsService
        .redeemArchivesReceipt(
          receiptCredentialPresentation
        )
    } else {
      Log.d(TAG, "Attempting to redeem donation receipt credential presentation...", true)
      AppDependencies
        .donationsService
        .redeemDonationReceipt(
          receiptCredentialPresentation,
          SignalStore.inAppPayments.getDisplayBadgesOnProfile(),
          jobData.makePrimary
        )
    }

    verifyServiceResponse(serviceResponse) {
      val protoError = InAppPaymentData.Error(
        type = InAppPaymentData.Error.Type.REDEMPTION,
        data_ = serviceResponse.status.toString()
      )

      SignalDatabase.inAppPayments.update(
        inAppPayment = inAppPayment.copy(
          data = inAppPayment.data.copy(
            error = protoError
          )
        )
      )
    }

    Log.i(TAG, "InAppPayment with ID $inAppPaymentId was successfully redeemed. Response code: ${serviceResponse.status}")
    SignalDatabase.inAppPayments.update(
      inAppPayment = inAppPayment.copy(
        state = InAppPaymentTable.State.END,
        data = inAppPayment.data.copy(
          redemption = inAppPayment.data.redemption.copy(
            stage = InAppPaymentData.RedemptionState.Stage.REDEEMED
          )
        )
      )
    )

    if (inAppPayment.type == InAppPaymentType.RECURRING_BACKUP) {
      Log.i(TAG, "Setting backup tier to PAID", true)
      SignalStore.backup.backupTier = MessageBackupTier.PAID
    }
  }

  private fun <T> verifyServiceResponse(serviceResponse: ServiceResponse<T>, onFatalError: (Int) -> Unit = {}) {
    if (serviceResponse.executionError.isPresent) {
      val error = serviceResponse.executionError.get()
      Log.w(TAG, "Encountered a retryable error.", error, true)
      throw InAppPaymentRetryException(error)
    }

    if (serviceResponse.applicationError.isPresent) {
      val error = serviceResponse.applicationError.get()
      if (serviceResponse.status >= 500) {
        Log.w(TAG, "Encountered a retryable service error", error, true)
        throw InAppPaymentRetryException(error)
      } else {
        Log.w(TAG, "Encountered a non-recoverable error", error, true)
        onFatalError(serviceResponse.status)

        throw IOException(error)
      }
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = e is InAppPaymentRetryException

  class Factory : Job.Factory<InAppPaymentRedemptionJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InAppPaymentRedemptionJob {
      return InAppPaymentRedemptionJob(
        InAppPaymentRedemptionJobData.ADAPTER.decode(serializedData!!),
        parameters
      )
    }
  }
}
