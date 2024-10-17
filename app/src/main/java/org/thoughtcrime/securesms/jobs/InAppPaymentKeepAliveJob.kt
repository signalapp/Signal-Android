/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toDecimalValue
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.DonationRedemptionJobStatus
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.DonationRedemptionJobWatcher
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.FiatValue
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.internal.EmptyResponse
import org.whispersystems.signalservice.internal.ServiceResponse
import java.util.Locale
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Checks whether we need to create and process a new InAppPurchase for a new month
 */
class InAppPaymentKeepAliveJob private constructor(
  parameters: Parameters,
  val type: InAppPaymentSubscriberRecord.Type
) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(InAppPaymentKeepAliveJob::class.java)

    const val KEY = "InAppPurchaseRecurringKeepAliveJob"

    private val TIMEOUT = 3.days

    const val KEEP_ALIVE = "keep-alive"
    private const val DATA_TYPE = "type"

    fun create(type: InAppPaymentSubscriberRecord.Type): Job {
      return InAppPaymentKeepAliveJob(
        parameters = Parameters.Builder()
          .setQueue(type.jobQueue)
          .addConstraint(NetworkConstraint.KEY)
          .setMaxInstancesForQueue(1)
          .setMaxAttempts(Parameters.UNLIMITED)
          .setLifespan(TIMEOUT.inWholeSeconds)
          .build(),
        type = type
      )
    }

    @JvmStatic
    fun enqueueAndTrackTimeIfNecessary() {
      // TODO -- This should only be enqueued if we are completely drained of old subscription jobs. (No pending, no runnning)
      val lastKeepAliveTime = SignalStore.inAppPayments.getLastKeepAliveLaunchTime().milliseconds
      val now = System.currentTimeMillis().milliseconds

      if (lastKeepAliveTime > now) {
        enqueueAndTrackTime(now)
        return
      }

      val nextLaunchTime = lastKeepAliveTime + 3.days
      if (nextLaunchTime <= now) {
        enqueueAndTrackTime(now)
      }
    }

    @JvmStatic
    fun enqueueAndTrackTime(now: Duration) {
      AppDependencies.jobManager.add(create(InAppPaymentSubscriberRecord.Type.DONATION))
      AppDependencies.jobManager.add(create(InAppPaymentSubscriberRecord.Type.BACKUP))
      SignalStore.inAppPayments.setLastKeepAliveLaunchTime(now.inWholeMilliseconds)
    }
  }

  override fun onRun() {
    synchronized(type) {
      doRun()
    }
  }

  private fun doRun() {
    val subscriber: InAppPaymentSubscriberRecord? = InAppPaymentsRepository.getSubscriber(type)
    if (subscriber == null) {
      info(type, "Subscriber not present. Skipping.")
      return
    }

    val response: ServiceResponse<EmptyResponse> = AppDependencies.donationsService.putSubscription(subscriber.subscriberId)

    verifyResponse(response)
    info(type, "Successful call to putSubscription")

    val activeSubscriptionResponse: ServiceResponse<ActiveSubscription> = AppDependencies.donationsService.getSubscription(subscriber.subscriberId)

    verifyResponse(activeSubscriptionResponse)
    info(type, "Successful call to GET active subscription")

    val activeSubscription: ActiveSubscription? = activeSubscriptionResponse.result.getOrNull()
    if (activeSubscription == null) {
      warn(type, "Failed to parse active subscription from response body. Exiting.")
      return
    }

    val subscription: ActiveSubscription.Subscription? = activeSubscription.activeSubscription
    if (subscription == null) {
      info(type, "User does not have a subscription. Exiting.")
      return
    }

    // Note that this can be removed once the old jobs are decommissioned. These jobs live in different queues, and should still be respected.
    if (type == InAppPaymentSubscriberRecord.Type.DONATION) {
      val legacyRedemptionStatus = DonationRedemptionJobWatcher.getSubscriptionRedemptionJobStatus()
      if (legacyRedemptionStatus != DonationRedemptionJobStatus.None && legacyRedemptionStatus != DonationRedemptionJobStatus.FailedSubscription) {
        info(type, "Already trying to redeem donation, current status: ${legacyRedemptionStatus.javaClass.simpleName}")
        return
      }
    }

    val activeInAppPayment = getActiveInAppPayment(subscriber, subscription)
    if (activeInAppPayment == null) {
      warn(type, "Failed to generate active in-app payment. Exiting")
      return
    }

    if (activeInAppPayment.state == InAppPaymentTable.State.END) {
      warn(type, "Active in-app payment is in the END state. Cannot proceed.")
      warn(type, "Active in-app payment cancel state: ${activeInAppPayment.data.cancellation}")
      return
    }

    info(type, "Processing id: ${activeInAppPayment.id}")

    when (activeInAppPayment.data.redemption?.stage) {
      InAppPaymentData.RedemptionState.Stage.INIT -> {
        info(type, "Transitioning payment from INIT to CONVERSION_STARTED and generating a request credential")
        val payment = activeInAppPayment.copy(
          data = activeInAppPayment.data.copy(
            redemption = activeInAppPayment.data.redemption.copy(
              stage = InAppPaymentData.RedemptionState.Stage.CONVERSION_STARTED,
              receiptCredentialRequestContext = InAppPaymentsRepository.generateRequestCredential().serialize().toByteString()
            )
          )
        )

        SignalDatabase.inAppPayments.update(payment)
        InAppPaymentRecurringContextJob.createJobChain(payment).enqueue()
      }
      InAppPaymentData.RedemptionState.Stage.CONVERSION_STARTED -> {
        if (activeInAppPayment.data.redemption.receiptCredentialRequestContext == null) {
          warn(type, "We are in the CONVERSION_STARTED state without a request credential. Exiting.")
          return
        }

        info(type, "We have a request credential we have not turned into a token.")
        InAppPaymentRecurringContextJob.createJobChain(activeInAppPayment).enqueue()
      }
      InAppPaymentData.RedemptionState.Stage.REDEMPTION_STARTED -> {
        if (activeInAppPayment.data.redemption.receiptCredentialPresentation == null) {
          warn(type, "We are in the REDEMPTION_STARTED state without a request credential. Exiting.")
          return
        }

        info(type, "We have a receipt credential presentation but have not yet redeemed it.")
        InAppPaymentRedemptionJob.enqueueJobChainForRecurringKeepAlive(activeInAppPayment)
      }
      else -> info(type, "Nothing to do. Exiting.")
    }
  }

  private fun <T> verifyResponse(serviceResponse: ServiceResponse<T>) {
    if (serviceResponse.executionError.isPresent) {
      val error = serviceResponse.executionError.get()
      warn(type, "Failed with an execution error. Scheduling retry.", error)
      throw InAppPaymentRetryException(error)
    }

    if (serviceResponse.applicationError.isPresent) {
      val error = serviceResponse.applicationError.get()
      when (serviceResponse.status) {
        403, 404 -> {
          warn(type, "Invalid or malformed subscriber id. Status: ${serviceResponse.status}", error)
        }
        else -> {
          warn(type, "An unknown server error occurred: ${serviceResponse.status}", error)
          throw InAppPaymentRetryException(error)
        }
      }
    }
  }

  private fun getActiveInAppPayment(
    subscriber: InAppPaymentSubscriberRecord,
    subscription: ActiveSubscription.Subscription
  ): InAppPaymentTable.InAppPayment? {
    val endOfCurrentPeriod = subscription.endOfCurrentPeriod.seconds
    val type = subscriber.type
    val current: InAppPaymentTable.InAppPayment? = SignalDatabase.inAppPayments.getByEndOfPeriod(type.inAppPaymentType, endOfCurrentPeriod)

    return if (current == null) {
      val oldInAppPayment = SignalDatabase.inAppPayments.getByLatestEndOfPeriod(type.inAppPaymentType)
      val oldEndOfPeriod = oldInAppPayment?.endOfPeriod ?: InAppPaymentsRepository.getFallbackLastEndOfPeriod(type)
      if (oldEndOfPeriod > endOfCurrentPeriod) {
        warn(type, "Active subscription returned an old end-of-period. Exiting. (old: $oldEndOfPeriod, new: $endOfCurrentPeriod)")
        return null
      }

      val badge = if (oldInAppPayment == null) {
        info(type, "Old payment not found in database. Loading badge / label information from donations configuration.")
        val configuration = AppDependencies.donationsService.getDonationsConfiguration(Locale.getDefault())
        if (configuration.result.isPresent) {
          val subscriptionConfig = configuration.result.get().levels[subscription.level]
          if (subscriptionConfig == null) {
            info(type, "Failed to load subscription configuration for level ${subscription.level} for type $type")
            null
          } else {
            Badges.toDatabaseBadge(Badges.fromServiceBadge(subscriptionConfig.badge))
          }
        } else {
          warn(TAG, "Failed to load configuration while processing $type")
          null
        }
      } else {
        oldInAppPayment.data.badge
      }

      info(type, "End of period has changed. Requesting receipt refresh. (old: $oldEndOfPeriod, new: $endOfCurrentPeriod)")
      if (type == InAppPaymentSubscriberRecord.Type.DONATION) {
        SignalStore.inAppPayments.setLastEndOfPeriod(endOfCurrentPeriod.inWholeSeconds)
      }

      val inAppPaymentId = SignalDatabase.inAppPayments.insert(
        type = type.inAppPaymentType,
        state = InAppPaymentTable.State.PENDING,
        subscriberId = subscriber.subscriberId,
        endOfPeriod = endOfCurrentPeriod,
        inAppPaymentData = InAppPaymentData(
          paymentMethodType = subscriber.paymentMethodType,
          badge = badge,
          amount = FiatValue(
            currencyCode = subscriber.currency.currencyCode,
            amount = subscription.amount.toDecimalValue()
          ),
          error = null,
          level = subscription.level.toLong(),
          cancellation = null,
          recipientId = null,
          additionalMessage = null,
          redemption = InAppPaymentData.RedemptionState(
            stage = InAppPaymentData.RedemptionState.Stage.INIT,
            keepAlive = true
          )
        )
      )

      MultiDeviceSubscriptionSyncRequestJob.enqueue()
      SignalDatabase.inAppPayments.getById(inAppPaymentId)
    } else if (current.state == InAppPaymentTable.State.PENDING && current.data.error?.data_ == KEEP_ALIVE) {
      info(type, "Found failed keep-alive. Retrying.")
      SignalDatabase.inAppPayments.update(
        current.copy(
          data = current.data.copy(
            error = null
          )
        )
      )

      SignalDatabase.inAppPayments.getById(current.id)
    } else if (current.state == InAppPaymentTable.State.END && current.data.error != null && current.data.paymentMethodType == InAppPaymentData.PaymentMethodType.UNKNOWN && subscriber.paymentMethodType.toPaymentSourceType().isBankTransfer) {
      info(type, "Found failed SEPA payment but there's no payment method assigned. Assigning payment method and retrying.")
      SignalDatabase.inAppPayments.update(
        current.copy(
          state = InAppPaymentTable.State.PENDING,
          data = current.data.copy(
            paymentMethodType = subscriber.paymentMethodType,
            error = null
          )
        )
      )

      SignalDatabase.inAppPayments.getById(current.id)
    } else {
      current
    }
  }

  private fun info(type: InAppPaymentSubscriberRecord.Type, message: String) {
    Log.i(TAG, "[$type] $message", true)
  }

  private fun warn(type: InAppPaymentSubscriberRecord.Type, message: String, throwable: Throwable? = null) {
    Log.w(TAG, "[$type] $message", throwable, true)
  }

  override fun serialize(): ByteArray? = JsonJobData.Builder().putInt(DATA_TYPE, type.code).build().serialize()

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onShouldRetry(e: Exception): Boolean = e is InAppPaymentRetryException

  class Factory : Job.Factory<InAppPaymentKeepAliveJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InAppPaymentKeepAliveJob {
      return InAppPaymentKeepAliveJob(
        parameters,
        InAppPaymentSubscriberRecord.Type.values().first { it.code == JsonJobData.deserialize(serializedData).getInt(DATA_TYPE) }
      )
    }
  }
}
