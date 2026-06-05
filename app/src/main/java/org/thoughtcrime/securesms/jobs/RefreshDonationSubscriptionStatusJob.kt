package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.network.exceptions.PushNetworkException
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.NotPushRegisteredException
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Refreshes the local view of the user's recurring donation subscription by querying
 * the server with the locally-known subscriber id and updating
 * [org.thoughtcrime.securesms.keyvalue.InAppPaymentValues.setLastEndOfPeriod] when the
 * server reports an active subscription with a newer end-of-period.
 *
 * Used to handle incoming `FetchLatest(SUBSCRIPTION_STATUS)` sync messages on linked
 * devices, where redemption never runs and `lastEndOfPeriod` would otherwise stay zero,
 * causing [org.thoughtcrime.securesms.keyvalue.InAppPaymentValues.isLikelyASustainer] to
 * report false even when the user is in fact subscribed.
 */
class RefreshDonationSubscriptionStatusJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY = "RefreshDonationSubscriptionStatusJob"

    private val TAG = Log.tag(RefreshDonationSubscriptionStatusJob::class.java)

    @JvmStatic
    fun enqueue() {
      val job = RefreshDonationSubscriptionStatusJob(
        Parameters.Builder()
          .setQueue(KEY)
          .addConstraint(NetworkConstraint.KEY)
          .setMaxInstancesForFactory(1)
          .setMaxAttempts(10)
          .build()
      )

      AppDependencies.jobManager.add(job)
    }
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    if (!Recipient.self().isRegistered) {
      throw NotPushRegisteredException()
    }

    if (!SignalStore.account.isLinkedDevice) {
      Log.i(TAG, "Not a linked device, skipping.")
      return
    }

    val subscriber = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)
    if (subscriber == null) {
      Log.i(TAG, "No local donation subscriber id, nothing to refresh.")
      return
    }

    val activeSubscription = AppDependencies.donationsService.getSubscription(subscriber.subscriberId).resultOrThrow

    if (activeSubscription.isActive) {
      val endOfCurrentPeriod = activeSubscription.activeSubscription.endOfCurrentPeriod
      if (endOfCurrentPeriod > SignalStore.inAppPayments.getLastEndOfPeriod()) {
        Log.i(TAG, "Server reports active subscription with newer end-of-period. Updating local state.")
        SignalStore.inAppPayments.setLastEndOfPeriod(endOfCurrentPeriod)
      } else {
        Log.i(TAG, "Server reports active subscription but local end-of-period is already current.")
      }
    } else {
      Log.i(TAG, "Server reports no active subscription.")
    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is PushNetworkException
  }

  class Factory : Job.Factory<RefreshDonationSubscriptionStatusJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RefreshDonationSubscriptionStatusJob {
      return RefreshDonationSubscriptionStatusJob(parameters)
    }
  }
}
