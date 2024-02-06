package org.thoughtcrime.securesms.contacts.sync

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.contacts.SystemContactsRepository
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.contacts.sync.FuzzyPhoneNumberHelper.InputResult
import org.thoughtcrime.securesms.contacts.sync.FuzzyPhoneNumberHelper.OutputResult
import org.thoughtcrime.securesms.database.RecipientTable.CdsV2Result
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.FeatureFlags
import org.whispersystems.signalservice.api.push.exceptions.CdsiInvalidTokenException
import org.whispersystems.signalservice.api.push.exceptions.CdsiResourceExhaustedException
import org.whispersystems.signalservice.api.services.CdsiV2Service
import java.io.IOException
import java.util.Optional
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Performs a CDS refresh using CDSv2.
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
  fun refreshAll(context: Context, timeoutMs: Long? = null): ContactDiscovery.RefreshResult {
    val recipientE164s: Set<String> = SignalDatabase.recipients.getAllE164s().sanitize()
    val systemE164s: Set<String> = SystemContactsRepository.getAllDisplayNumbers(context).toE164s(context).sanitize()

    return refreshInternal(
      recipientE164s = recipientE164s,
      systemE164s = systemE164s,
      inputPreviousE164s = SignalDatabase.cds.getAllE164s(),
      isPartialRefresh = false,
      timeoutMs = timeoutMs
    )
  }

  @Throws(IOException::class)
  @WorkerThread
  @Synchronized
  @JvmStatic
  fun refresh(context: Context, inputRecipients: List<Recipient>, timeoutMs: Long? = null): ContactDiscovery.RefreshResult {
    val recipients: List<Recipient> = inputRecipients.map { it.resolve() }
    val inputE164s: Set<String> = recipients.mapNotNull { it.e164.orElse(null) }.toSet().sanitize()

    return if (inputE164s.size > MAXIMUM_ONE_OFF_REQUEST_SIZE) {
      Log.i(TAG, "List of specific recipients to refresh is too large! (Size: ${recipients.size}). Doing a full refresh instead.")

      val fullResult: ContactDiscovery.RefreshResult = refreshAll(context, timeoutMs = timeoutMs)
      val inputIds: Set<RecipientId> = recipients.map { it.id }.toSet()

      ContactDiscovery.RefreshResult(
        registeredIds = fullResult.registeredIds.intersect(inputIds),
        rewrites = fullResult.rewrites.filterKeys { inputE164s.contains(it) }
      )
    } else {
      refreshInternal(
        recipientE164s = inputE164s,
        systemE164s = inputE164s,
        inputPreviousE164s = emptySet(),
        isPartialRefresh = true,
        timeoutMs = timeoutMs
      )
    }
  }

  @Throws(IOException::class)
  private fun refreshInternal(
    recipientE164s: Set<String>,
    systemE164s: Set<String>,
    inputPreviousE164s: Set<String>,
    isPartialRefresh: Boolean,
    timeoutMs: Long? = null
  ): ContactDiscovery.RefreshResult {
    val tag = "refreshInternal-v2"
    val stopwatch = Stopwatch(tag)

    val previousE164s: Set<String> = if (SignalStore.misc().cdsToken != null && !isPartialRefresh) inputPreviousE164s else emptySet()

    val allE164s: Set<String> = recipientE164s + systemE164s
    val newRawE164s: Set<String> = allE164s - previousE164s
    val fuzzyInput: InputResult = FuzzyPhoneNumberHelper.generateInput(newRawE164s, recipientE164s)
    val newE164s: Set<String> = fuzzyInput.numbers

    if (newE164s.isEmpty() && previousE164s.isEmpty()) {
      Log.w(TAG, "[$tag] No data to send! Ignoring.")
      return ContactDiscovery.RefreshResult(emptySet(), emptyMap())
    }

    if (newE164s.size > FeatureFlags.cdsHardLimit()) {
      Log.w(TAG, "[$tag] Number of new contacts (${newE164s.size.roundedString()} > hard limit (${FeatureFlags.cdsHardLimit()}! Failing and marking ourselves as permanently blocked.")
      SignalStore.misc().markCdsPermanentlyBlocked()
      throw IOException("New contacts over the CDS hard limit!")
    }

    val token: ByteArray? = if (previousE164s.isNotEmpty() && !isPartialRefresh) SignalStore.misc().cdsToken else null

    stopwatch.split("preamble")

    val response: CdsiV2Service.Response = try {
      ApplicationDependencies.getSignalServiceAccountManager().getRegisteredUsersWithCdsi(
        previousE164s,
        newE164s,
        SignalDatabase.recipients.getAllServiceIdProfileKeyPairs(),
        Optional.ofNullable(token),
        BuildConfig.CDSI_MRENCLAVE,
        timeoutMs
      ) { tokenToSave ->
        stopwatch.split("network-pre-token")
        if (!isPartialRefresh) {
          SignalStore.misc().cdsToken = tokenToSave
          SignalDatabase.cds.updateAfterFullCdsQuery(previousE164s + newE164s, allE164s + newE164s)
          Log.d(TAG, "Token saved!")
        } else {
          SignalDatabase.cds.updateAfterPartialCdsQuery(newE164s)
          Log.d(TAG, "Ignoring token.")
        }
        stopwatch.split("cds-db")
      }
    } catch (e: CdsiResourceExhaustedException) {
      Log.w(TAG, "CDS resource exhausted! Can try again in ${e.retryAfterSeconds} seconds.")
      SignalStore.misc().cdsBlockedUtil = System.currentTimeMillis() + e.retryAfterSeconds.seconds.inWholeMilliseconds
      throw e
    } catch (e: CdsiInvalidTokenException) {
      Log.w(TAG, "Our token was invalid! Only thing we can do now is clear our local state :(")
      SignalStore.misc().cdsToken = null
      SignalDatabase.cds.clearAll()
      throw e
    }

    if (!isPartialRefresh && SignalStore.misc().isCdsBlocked) {
      Log.i(TAG, "Successfully made a request while blocked -- clearing blocked state.")
      SignalStore.misc().clearCdsBlocked()
    }

    Log.d(TAG, "[$tag] Used ${response.quotaUsedDebugOnly} quota.")
    stopwatch.split("network-post-token")

    val registeredIds: MutableSet<RecipientId> = mutableSetOf()
    val rewrites: MutableMap<String, String> = mutableMapOf()

    val transformed: Map<String, CdsV2Result> = response.results.mapValues { entry -> CdsV2Result(entry.value.pni, entry.value.aci.orElse(null)) }
    val fuzzyOutput: OutputResult<CdsV2Result> = FuzzyPhoneNumberHelper.generateOutput(transformed, fuzzyInput)

    SignalDatabase.recipients.rewritePhoneNumbers(fuzzyOutput.rewrites)
    stopwatch.split("rewrite-e164")

    registeredIds += SignalDatabase.recipients.bulkProcessCdsResult(fuzzyOutput.numbers)
    rewrites += fuzzyOutput.rewrites
    stopwatch.split("process-result")

    val existingIds: Set<RecipientId> = SignalDatabase.recipients.getAllPossiblyRegisteredByE164(recipientE164s + rewrites.values)
    stopwatch.split("get-ids")

    val inactiveIds: Set<RecipientId> = (existingIds - registeredIds).removePossiblyRegisteredButUndiscoverable()
    stopwatch.split("registered-but-unlisted")

    val missingFromCds: Set<RecipientId> = existingIds - registeredIds
    SignalDatabase.recipients.updatePhoneNumberDiscoverability(registeredIds, missingFromCds)

    SignalDatabase.recipients.bulkUpdatedRegisteredStatus(registeredIds, inactiveIds)
    stopwatch.split("update-registered")

    stopwatch.stop(TAG)

    return ContactDiscovery.RefreshResult(registeredIds, rewrites)
  }

  private fun hasCommunicatedWith(recipient: Recipient): Boolean {
    val localAci = SignalStore.account().requireAci()
    return SignalDatabase.threads.hasActiveThread(recipient.id) || (recipient.hasServiceId() && SignalDatabase.sessions.hasSessionFor(localAci, recipient.requireServiceId().toString()))
  }

  /**
   * If an account is undiscoverable, it won't come back in the CDS response. So just because we're missing a entry doesn't mean they've become unregistered.
   * This function removes people from the list that both have a serviceId and some history of communication. We consider this a good heuristic for
   * "maybe this person just removed themselves from CDS". We'll rely on profile fetches that occur during chat opens to check registered status and clear
   * actually-unregistered users out.
   */
  @WorkerThread
  private fun Set<RecipientId>.removePossiblyRegisteredButUndiscoverable(): Set<RecipientId> {
    val selfId = Recipient.self().id
    return this - Recipient.resolvedList(this)
      .filter {
        (it.hasServiceId() && hasCommunicatedWith(it)) || it.id == selfId
      }
      .map { it.id }
      .toSet()
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

  private fun Int.roundedString(): String {
    val nearestThousand = (this.toDouble() / 1000).roundToInt()
    return "~${nearestThousand}k"
  }
}
