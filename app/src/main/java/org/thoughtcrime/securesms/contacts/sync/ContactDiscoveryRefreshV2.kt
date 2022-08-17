package org.thoughtcrime.securesms.contacts.sync

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.contacts.SystemContactsRepository
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.services.CdsiV2Service
import java.io.IOException
import java.util.Optional

/**
 * Performs the CDS refresh using the V2 interface (either CDSH or CDSI) that returns both PNIs and ACIs.
 */
object ContactDiscoveryRefreshV2 {

  // Using Log.tag will cut off the version number
  private const val TAG = "CdsRefreshV2"

  /**
   * The maximum number items we will allow in a 'one-off' request.
   * One-off requests, while much faster, will always deduct the request size from our rate limit.
   * So we need to be careful about making it too large.
   * If a request size is over this limit, we will always fall back to a full sync.
   */
  private const val MAXIMUM_ONE_OFF_REQUEST_SIZE = 3

  @Throws(IOException::class)
  @WorkerThread
  @Synchronized
  @JvmStatic
  fun refreshAll(context: Context, ignoreResults: Boolean = false): ContactDiscovery.RefreshResult {
    val stopwatch = Stopwatch("refresh-all")

    val previousE164s: Set<String> = if (SignalStore.misc().cdsToken != null) {
      SignalDatabase.cds.getAllE164s()
    } else {
      Log.w(TAG, "No token set! Cannot provide previousE164s.")
      emptySet()
    }
    stopwatch.split("previous")

    val recipientE164s: Set<String> = SignalDatabase.recipients.getAllE164s().sanitize()
    val newRecipientE164s: Set<String> = recipientE164s - previousE164s
    stopwatch.split("recipient")

    val systemE164s: Set<String> = SystemContactsRepository.getAllDisplayNumbers(context).toE164s(context).sanitize()
    val newSystemE164s: Set<String> = systemE164s - previousE164s
    stopwatch.split("system")

    val newE164s: Set<String> = newRecipientE164s + newSystemE164s

    val tokenToUse: ByteArray? = if (previousE164s.isNotEmpty()) {
      SignalStore.misc().cdsToken
    } else {
      if (SignalStore.misc().cdsToken != null) {
        Log.w(TAG, "We have a token, but our previousE164 list is empty! We cannot provide a token.")
      }
      null
    }

    val response: CdsiV2Service.Response = makeRequest(
      previousE164s = previousE164s,
      newE164s = newE164s,
      serviceIds = SignalDatabase.recipients.getAllServiceIdProfileKeyPairs(),
      token = tokenToUse,
      saveToken = true,
      tag = "refresh-all"
    )
    stopwatch.split("network")

    SignalDatabase.cds.updateAfterCdsQuery(newE164s, recipientE164s + systemE164s)
    stopwatch.split("cds-db")

    var registeredIds: Set<RecipientId> = emptySet()

    if (ignoreResults) {
      Log.w(TAG, "[refresh-all] Ignoring CDSv2 results.")
    } else {
      registeredIds = SignalDatabase.recipients.bulkProcessCdsV2Result(
        response.results
          .mapValues { entry -> RecipientDatabase.CdsV2Result(entry.value.pni, entry.value.aci.orElse(null)) }
      )
      stopwatch.split("recipient-db")

      SignalDatabase.recipients.bulkUpdatedRegisteredStatus(registeredIds.associateWith { null }, emptyList())
      stopwatch.split("update-registered")
    }

    stopwatch.stop(TAG)
    Log.d(TAG, "[refresh-all] Used ${response.quotaUsedDebugOnly} units of our quota.")

    return ContactDiscovery.RefreshResult(registeredIds, emptyMap())
  }

  @Throws(IOException::class)
  @WorkerThread
  @Synchronized
  @JvmStatic
  fun refresh(context: Context, inputRecipients: List<Recipient>, ignoreResults: Boolean = false): ContactDiscovery.RefreshResult {
    val stopwatch = Stopwatch("refresh-some")

    val recipients = inputRecipients.map { it.resolve() }
    stopwatch.split("resolve")

    val inputIds: Set<RecipientId> = recipients.map { it.id }.toSet()
    val inputE164s: Set<String> = recipients.mapNotNull { it.e164.orElse(null) }.toSet()

    if (inputE164s.size > MAXIMUM_ONE_OFF_REQUEST_SIZE) {
      Log.i(TAG, "List of specific recipients to refresh is too large! (Size: ${recipients.size}). Doing a full refresh instead.")
      val fullResult: ContactDiscovery.RefreshResult = refreshAll(context, ignoreResults)

      return ContactDiscovery.RefreshResult(
        registeredIds = fullResult.registeredIds.intersect(inputIds),
        rewrites = fullResult.rewrites.filterKeys { inputE164s.contains(it) }
      )
    }

    if (inputE164s.isEmpty()) {
      Log.w(TAG, "No numbers to refresh!")
      return ContactDiscovery.RefreshResult(emptySet(), emptyMap())
    } else {
      Log.i(TAG, "Doing a one-off request for ${inputE164s.size} recipients.")
    }

    val response: CdsiV2Service.Response = makeRequest(
      previousE164s = emptySet(),
      newE164s = inputE164s,
      serviceIds = SignalDatabase.recipients.getAllServiceIdProfileKeyPairs(),
      token = null,
      saveToken = false,
      tag = "refresh-some"
    )
    stopwatch.split("network")

    var registeredIds: Set<RecipientId> = emptySet()

    if (ignoreResults) {
      Log.w(TAG, "[refresh-some] Ignoring CDSv2 results.")
    } else {
      registeredIds = SignalDatabase.recipients.bulkProcessCdsV2Result(
        response.results
          .mapValues { entry -> RecipientDatabase.CdsV2Result(entry.value.pni, entry.value.aci.orElse(null)) }
      )
      stopwatch.split("recipient-db")

      SignalDatabase.recipients.bulkUpdatedRegisteredStatus(registeredIds.associateWith { null }, emptyList())
      stopwatch.split("update-registered")
    }

    Log.d(TAG, "[refresh-some] Used ${response.quotaUsedDebugOnly} units of our quota.")
    stopwatch.stop(TAG)

    return ContactDiscovery.RefreshResult(registeredIds, emptyMap())
  }

  @Throws(IOException::class)
  private fun makeRequest(previousE164s: Set<String>, newE164s: Set<String>, serviceIds: Map<ServiceId, ProfileKey>, token: ByteArray?, saveToken: Boolean, tag: String): CdsiV2Service.Response {
    return ApplicationDependencies.getSignalServiceAccountManager().getRegisteredUsersWithCdsi(
      previousE164s,
      newE164s,
      serviceIds,
      Optional.ofNullable(token),
      BuildConfig.CDSI_MRENCLAVE
    ) { tokenToSave ->
      if (saveToken) {
        SignalStore.misc().cdsToken = tokenToSave
        Log.d(TAG, "[$tag] Token saved!")
      }
    }
  }

  private fun Set<String>.toE164s(context: Context): Set<String> {
    return this.map { PhoneNumberFormatter.get(context).format(it) }.toSet()
  }

  private fun Set<String>.sanitize(): Set<String> {
    return this
      .filter {
        try {
          it.startsWith("+") && it.length > 1 && it[1] != '0' && it.toLong() > 0
        } catch (e: NumberFormatException) {
          false
        }
      }
      .toSet()
  }
}
