package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import io.reactivex.rxjava3.core.Observable
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.BoostReceiptRequestResponseJob
import org.thoughtcrime.securesms.jobs.DonationReceiptRedemptionJob
import org.thoughtcrime.securesms.jobs.ExternalLaunchDonationJob
import org.thoughtcrime.securesms.jobs.SubscriptionReceiptRequestResponseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Allows observer to poll for the status of the latest pending, running, or completed redemption job for subscriptions or one time payments.
 */
object DonationRedemptionJobWatcher {

  enum class RedemptionType {
    SUBSCRIPTION,
    ONE_TIME
  }

  fun watchSubscriptionRedemption(): Observable<Optional<JobTracker.JobState>> = watch(RedemptionType.SUBSCRIPTION)

  fun watchOneTimeRedemption(): Observable<Optional<JobTracker.JobState>> = watch(RedemptionType.ONE_TIME)

  private fun watch(redemptionType: RedemptionType): Observable<Optional<JobTracker.JobState>> = Observable.interval(0, 5, TimeUnit.SECONDS).map {
    val queue = when (redemptionType) {
      RedemptionType.SUBSCRIPTION -> DonationReceiptRedemptionJob.SUBSCRIPTION_QUEUE
      RedemptionType.ONE_TIME -> DonationReceiptRedemptionJob.ONE_TIME_QUEUE
    }

    val externalLaunchJobState: JobTracker.JobState? = ApplicationDependencies.getJobManager().getFirstMatchingJobState {
      it.factoryKey == ExternalLaunchDonationJob.KEY && it.parameters.queue?.startsWith(queue) == true
    }

    val redemptionJobState: JobTracker.JobState? = ApplicationDependencies.getJobManager().getFirstMatchingJobState {
      it.factoryKey == DonationReceiptRedemptionJob.KEY && it.parameters.queue?.startsWith(queue) == true
    }

    val receiptRequestJobKey = when (redemptionType) {
      RedemptionType.SUBSCRIPTION -> SubscriptionReceiptRequestResponseJob.KEY
      RedemptionType.ONE_TIME -> BoostReceiptRequestResponseJob.KEY
    }

    val receiptJobState: JobTracker.JobState? = ApplicationDependencies.getJobManager().getFirstMatchingJobState {
      it.factoryKey == receiptRequestJobKey && it.parameters.queue?.startsWith(queue) == true
    }

    val jobState: JobTracker.JobState? = externalLaunchJobState ?: redemptionJobState ?: receiptJobState

    if (redemptionType == RedemptionType.SUBSCRIPTION && jobState == null && SignalStore.donationsValues().getSubscriptionRedemptionFailed()) {
      Optional.of(JobTracker.JobState.FAILURE)
    } else {
      Optional.ofNullable(jobState)
    }
  }.distinctUntilChanged()
}
