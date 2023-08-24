package org.thoughtcrime.securesms.contacts.paged

import androidx.annotation.VisibleForTesting
import io.reactivex.rxjava3.core.Single
import org.signal.core.util.Stopwatch
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.concurrent.safeBlockingGet
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.storage.SignalIdentityKeyStore
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.IdentityUtil
import org.whispersystems.signalservice.api.services.ProfileService
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.push.IdentityCheckResponse
import java.util.concurrent.TimeUnit

/**
 * Generic repository for interacting with safety numbers and fetch new ones.
 */
class SafetyNumberRepository(
  private val profileService: ProfileService = ApplicationDependencies.getProfileService(),
  private val aciIdentityStore: SignalIdentityKeyStore = ApplicationDependencies.getProtocolStore().aci().identities()
) {

  private val recentlyFetched: MutableMap<RecipientId, Long> = HashMap()

  fun batchSafetyNumberCheck(newSelectionEntries: List<ContactSearchKey>) {
    SignalExecutors.UNBOUNDED.execute {
      try {
        batchSafetyNumberCheckSync(newSelectionEntries)
      } catch (e: InterruptedException) {
        Log.w(TAG, "Unable to fetch safety number change", e)
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  @VisibleForTesting
  @Throws(InterruptedException::class)
  fun batchSafetyNumberCheckSync(newSelectionEntries: List<ContactSearchKey>, now: Long = System.currentTimeMillis(), batchSize: Int = MAX_BATCH_SIZE) {
    val stopwatch = Stopwatch("batch-snc")
    val recipientIds: Set<RecipientId> = newSelectionEntries.flattenToRecipientIds()
    stopwatch.split("recipient-ids")

    val recentIds = recentlyFetched.filter { (_, timestamp) -> (now - timestamp) < RECENT_TIME_WINDOW }.keys
    val recipients = Recipient.resolvedList(recipientIds - recentIds).filter { it.hasServiceId() }
    stopwatch.split("recipient-resolve")

    if (recipients.isNotEmpty()) {
      Log.i(TAG, "Checking on ${recipients.size} identities...")
      val requests: List<Single<List<IdentityCheckResponse.ServiceIdentityPair>>> = recipients.chunked(batchSize) { it.createBatchRequestSingle() }
      stopwatch.split("requests")

      val aciKeyPairs: List<IdentityCheckResponse.ServiceIdentityPair> = Single.zip(requests) { responses ->
        responses
          .map { it as List<IdentityCheckResponse.ServiceIdentityPair> }
          .flatten()
      }.safeBlockingGet()

      stopwatch.split("batch-fetches")

      if (aciKeyPairs.isEmpty()) {
        Log.d(TAG, "No identity key mismatches")
      } else {
        aciKeyPairs
          .filter { it.serviceId != null && it.identityKey != null }
          .forEach { IdentityUtil.saveIdentity(it.serviceId.toString(), it.identityKey) }
      }
      recentlyFetched += recipients.associate { it.id to now }
      stopwatch.split("saving-identities")
    }
    stopwatch.stop(TAG)
  }

  private fun List<ContactSearchKey>.flattenToRecipientIds(): Set<RecipientId> {
    return this
      .map {
        when {
          it is ContactSearchKey.RecipientSearchKey && !it.isStory -> {
            val recipient = Recipient.resolved(it.recipientId)
            if (recipient.isGroup) {
              recipient.participantIds
            } else {
              listOf(it.recipientId)
            }
          }
          it is ContactSearchKey.RecipientSearchKey -> Recipient.resolved(it.recipientId).participantIds
          else -> throw AssertionError("Invalid contact selection $it")
        }
      }
      .flatten()
      .toMutableSet()
      .apply { remove(Recipient.self().id) }
  }

  private fun List<Recipient>.createBatchRequestSingle(): Single<List<IdentityCheckResponse.ServiceIdentityPair>> {
    return profileService
      .performIdentityCheck(
        mapNotNull { r ->
          val identityRecord = aciIdentityStore.getIdentityRecord(r.id)
          if (identityRecord.isPresent) {
            r.requireServiceId() to identityRecord.get().identityKey
          } else {
            null
          }
        }.associate { it }
      )
      .map { ServiceResponseProcessor.DefaultProcessor(it).resultOrThrow.serviceIdKeyPairs ?: emptyList() }
      .onErrorReturn { t ->
        Log.w(TAG, "Unable to fetch identities", t)
        emptyList()
      }
  }

  companion object {
    private val TAG = Log.tag(SafetyNumberRepository::class.java)
    private val RECENT_TIME_WINDOW = TimeUnit.SECONDS.toMillis(30)
    private const val MAX_BATCH_SIZE = 1000
  }
}
