package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Observable
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.Stripe3DSData
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.jobs.BoostReceiptRequestResponseJob
import org.thoughtcrime.securesms.jobs.DonationReceiptRedemptionJob
import org.thoughtcrime.securesms.jobs.ExternalLaunchDonationJob
import org.thoughtcrime.securesms.jobs.SubscriptionReceiptRequestResponseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.util.concurrent.TimeUnit

/**
 * Allows observer to poll for the status of the latest pending, running, or completed redemption job for subscriptions or one time payments.
 *
 * @deprecated This object is deprecated and will be removed once we are sure all jobs have drained.
 */
object DonationRedemptionJobWatcher {

  enum class RedemptionType {
    SUBSCRIPTION,
    ONE_TIME
  }

  @WorkerThread
  fun hasPendingRedemptionJob(): Boolean {
    return getDonationRedemptionJobStatus(RedemptionType.SUBSCRIPTION).isInProgress() || getDonationRedemptionJobStatus(RedemptionType.ONE_TIME).isInProgress()
  }

  fun watchSubscriptionRedemption(): Observable<DonationRedemptionJobStatus> = watch(RedemptionType.SUBSCRIPTION)

  @JvmStatic
  @WorkerThread
  fun getSubscriptionRedemptionJobStatus(): DonationRedemptionJobStatus {
    return getDonationRedemptionJobStatus(RedemptionType.SUBSCRIPTION)
  }

  fun watchOneTimeRedemption(): Observable<DonationRedemptionJobStatus> = watch(RedemptionType.ONE_TIME)

  private fun watch(redemptionType: RedemptionType): Observable<DonationRedemptionJobStatus> {
    return Observable
      .interval(0, 5, TimeUnit.SECONDS)
      .map {
        getDonationRedemptionJobStatus(redemptionType)
      }
      .distinctUntilChanged()
  }

  private fun getDonationRedemptionJobStatus(redemptionType: RedemptionType): DonationRedemptionJobStatus {
    val queue = when (redemptionType) {
      RedemptionType.SUBSCRIPTION -> DonationReceiptRedemptionJob.SUBSCRIPTION_QUEUE
      RedemptionType.ONE_TIME -> DonationReceiptRedemptionJob.ONE_TIME_QUEUE
    }

    val donationJobSpecs = AppDependencies
      .jobManager
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

    return if (redemptionType == RedemptionType.SUBSCRIPTION && jobSpec == null && SignalStore.inAppPayments.getSubscriptionRedemptionFailed()) {
      DonationRedemptionJobStatus.FailedSubscription
    } else {
      jobSpec?.toDonationRedemptionStatus(redemptionType) ?: DonationRedemptionJobStatus.None
    }
  }

  private fun JobSpec.toDonationRedemptionStatus(redemptionType: RedemptionType): DonationRedemptionJobStatus {
    return when (factoryKey) {
      ExternalLaunchDonationJob.KEY -> {
        val stripe3DSData = ExternalLaunchDonationJob.Factory.parseSerializedData(serializedData!!)
        DonationRedemptionJobStatus.PendingExternalVerification(
          pendingOneTimeDonation = pendingOneTimeDonation(redemptionType, stripe3DSData),
          nonVerifiedMonthlyDonation = nonVerifiedMonthlyDonation(redemptionType, stripe3DSData)
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

  private fun JobSpec.pendingOneTimeDonation(redemptionType: RedemptionType, stripe3DSData: Stripe3DSData): PendingOneTimeDonation? {
    if (redemptionType != RedemptionType.ONE_TIME) {
      return null
    }

    return DonationSerializationHelper.createPendingOneTimeDonationProto(
      badge = Badges.fromDatabaseBadge(stripe3DSData.inAppPayment.data.badge!!),
      paymentSourceType = stripe3DSData.paymentSourceType,
      amount = stripe3DSData.inAppPayment.data.amount!!.toFiatMoney()
    ).copy(
      timestamp = createTime,
      pendingVerification = true,
      checkedVerification = runAttempt > 0
    )
  }

  private fun JobSpec.nonVerifiedMonthlyDonation(redemptionType: RedemptionType, stripe3DSData: Stripe3DSData): NonVerifiedMonthlyDonation? {
    if (redemptionType != RedemptionType.SUBSCRIPTION) {
      return null
    }

    return NonVerifiedMonthlyDonation(
      timestamp = createTime,
      price = stripe3DSData.inAppPayment.data.amount!!.toFiatMoney(),
      level = stripe3DSData.inAppPayment.data.level.toInt(),
      checkedVerification = runAttempt > 0
    )
  }
}
