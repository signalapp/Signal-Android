package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import io.reactivex.rxjava3.core.Observable
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.jobs.BoostReceiptRequestResponseJob
import org.thoughtcrime.securesms.jobs.DonationReceiptRedemptionJob
import org.thoughtcrime.securesms.jobs.ExternalLaunchDonationJob
import org.thoughtcrime.securesms.jobs.SubscriptionReceiptRequestResponseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.util.concurrent.TimeUnit

/**
 * Allows observer to poll for the status of the latest pending, running, or completed redemption job for subscriptions or one time payments.
 */
object DonationRedemptionJobWatcher {

  enum class RedemptionType {
    SUBSCRIPTION,
    ONE_TIME
  }

  fun watchSubscriptionRedemption(): Observable<DonationRedemptionJobStatus> = watch(RedemptionType.SUBSCRIPTION)

  fun watchOneTimeRedemption(): Observable<DonationRedemptionJobStatus> = watch(RedemptionType.ONE_TIME)

  private fun watch(redemptionType: RedemptionType): Observable<DonationRedemptionJobStatus> = Observable.interval(0, 5, TimeUnit.SECONDS).map {
    val queue = when (redemptionType) {
      RedemptionType.SUBSCRIPTION -> DonationReceiptRedemptionJob.SUBSCRIPTION_QUEUE
      RedemptionType.ONE_TIME -> DonationReceiptRedemptionJob.ONE_TIME_QUEUE
    }

    val donationJobSpecs = ApplicationDependencies
      .getJobManager()
      .find { it.queueKey?.startsWith(queue) == true }
      .sortedBy { it.createTime }

    val externalLaunchJobSpec: JobSpec? = donationJobSpecs.firstOrNull {
      it.factoryKey == ExternalLaunchDonationJob.KEY
    }

    val receiptRequestJobKey = when (redemptionType) {
      RedemptionType.SUBSCRIPTION -> SubscriptionReceiptRequestResponseJob.KEY
      RedemptionType.ONE_TIME -> BoostReceiptRequestResponseJob.KEY
    }

    val receiptJobSpec: JobSpec? = donationJobSpecs.firstOrNull {
      it.factoryKey == receiptRequestJobKey
    }

    val redemptionJobSpec: JobSpec? = donationJobSpecs.firstOrNull {
      it.factoryKey == DonationReceiptRedemptionJob.KEY
    }

    val jobSpec: JobSpec? = externalLaunchJobSpec ?: redemptionJobSpec ?: receiptJobSpec

    if (redemptionType == RedemptionType.SUBSCRIPTION && jobSpec == null && SignalStore.donationsValues().getSubscriptionRedemptionFailed()) {
      DonationRedemptionJobStatus.FailedSubscription
    } else {
      jobSpec?.toDonationRedemptionStatus() ?: DonationRedemptionJobStatus.None
    }
  }.distinctUntilChanged()

  private fun JobSpec.toDonationRedemptionStatus(): DonationRedemptionJobStatus {
    return when (factoryKey) {
      ExternalLaunchDonationJob.KEY -> {
        val stripe3DSData = ExternalLaunchDonationJob.Factory.parseSerializedData(serializedData!!)
        DonationRedemptionJobStatus.PendingExternalVerification(
          pendingOneTimeDonation = DonationSerializationHelper.createPendingOneTimeDonationProto(
            badge = stripe3DSData.gatewayRequest.badge,
            paymentSourceType = stripe3DSData.paymentSourceType,
            amount = stripe3DSData.gatewayRequest.fiat
          ).copy(
            timestamp = createTime,
            pendingVerification = true,
            checkedVerification = runAttempt > 0
          )
        )
      }

      SubscriptionReceiptRequestResponseJob.KEY,
      BoostReceiptRequestResponseJob.KEY -> DonationRedemptionJobStatus.PendingReceiptRequest

      DonationReceiptRedemptionJob.KEY -> DonationRedemptionJobStatus.PendingReceiptRedemption

      else -> {
        DonationRedemptionJobStatus.None
      }
    }
  }
}
