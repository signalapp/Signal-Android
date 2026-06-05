/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.keys

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.network.NetworkResult
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalSessionLock
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.push.SignalServiceAddress

/**
 * Perform pre-key operations to establish sessions.
 */
class PreKeyRepository(
  private val keysApi: KeysApi,
  private val aciStore: SignalServiceAccountDataStore,
  private val localProtocolAddress: SignalProtocolAddress,
  private val batchHelper: BatchHelper
) {

  companion object {
    private val TAG = Log.tag(PreKeyRepository::class)

    private const val MAX_PARALLEL_FETCHES = 32

    @OptIn(ExperimentalCoroutinesApi::class)
    private val fetchDispatcher = Dispatchers.IO.limitedParallelism(MAX_PARALLEL_FETCHES, "PreKeyRepository")
  }

  /**
   * Wraps prekey fetching that initializes sessions in advance of a send.
   *
   * Network fetches run in parallel, once they all complete, sessions are built sequentially
   * under a single acquisition of the [SignalSessionLock] so individual fetch threads are not
   * competing for the lock.
   */
  fun eagerlyFetchMissingPreKeys(requests: List<EagerPreKeyRequest>, onSecurityEvent: ((SignalServiceAddress) -> Unit)? = null) {
    val stopwatch = Stopwatch("eagerPrefetch")

    val needsFetch = requests.filter { request ->
      val defaultAddress = SignalProtocolAddress(request.recipient.identifier, SignalServiceAddress.DEFAULT_DEVICE_ID)
      !aciStore.containsSession(defaultAddress)
    }
    stopwatch.split("filter")

    if (needsFetch.isEmpty()) {
      return
    }

    Log.i(TAG, "[eagerPrefetch] Attempting to fetch prekeys for ${needsFetch.size} recipients")

    val fetched: List<PreKeyFetchResult> = try {
      runBlocking { fetchAll(needsFetch) }
    } catch (e: InterruptedException) {
      Log.w(TAG, "[eagerPrefetch] Interrupted while fetching prekeys", e)
      return
    }
    stopwatch.split("fetch")

    val securityEventRecipients = applySessions(fetched)
    stopwatch.split("apply")

    if (onSecurityEvent != null) {
      for (recipient in securityEventRecipients) {
        onSecurityEvent(recipient)
      }
    }

    stopwatch.stop(TAG)
  }

  private suspend fun fetchAll(requests: List<EagerPreKeyRequest>): List<PreKeyFetchResult> = coroutineScope {
    val tasks: List<Deferred<PreKeyFetchResult>> = requests.map { request ->
      async(fetchDispatcher) {
        when (val result = getPreKeys(request)) {
          is NetworkResult.Success -> PreKeyFetchResult.Success(request.recipient, result.result)
          else -> {
            Log.d(TAG, "[eagerPrefetch] Network issue encountered for ${request.recipient.identifier}")
            PreKeyFetchResult.Failure
          }
        }
      }
    }

    try {
      tasks.awaitAll()
    } catch (e: Exception) {
      Log.w(TAG, "Hit an exception that caused us to end early.", e)
      emptyList()
    }
  }

  private suspend fun getPreKeys(request: EagerPreKeyRequest): NetworkResult<List<PreKeyBundle>> {
    val sealedSenderAccess = if (request.story && SealedSenderAccess.isUnrestrictedForStory(request.sealedSenderAccess)) null else request.sealedSenderAccess

    val response = keysApi.getPreKeys(request.recipient, sealedSenderAccess, SignalServiceAddress.DEFAULT_DEVICE_ID)

    if (response is NetworkResult.StatusCodeError && response.code == 401 && request.story) {
      Log.d(TAG, "Got 401 when fetching prekey for story. Trying without UD.")
      return keysApi.getPreKeys(request.recipient, null, SignalServiceAddress.DEFAULT_DEVICE_ID)
    }

    return response
  }

  private fun applySessions(results: List<PreKeyFetchResult>): List<SignalServiceAddress> {
    if (results.isEmpty()) {
      return emptyList()
    }

    val securityEventRecipients = mutableListOf<SignalServiceAddress>()

    batchHelper.batch {
      for (result in results) {
        if (result !is PreKeyFetchResult.Success) continue

        val recipient = result.recipient
        var aborted = false

        for (preKey in result.bundles) {
          val preKeyAddress = SignalProtocolAddress(recipient.identifier, preKey.deviceId)
          try {
            SessionBuilder(aciStore, preKeyAddress, localProtocolAddress).process(preKey)
          } catch (_: UntrustedIdentityException) {
            Log.i(TAG, "[eagerPrefetch] Untrusted identity for recipient")
            aborted = true
            break
          } catch (_: InvalidKeyException) {
            Log.i(TAG, "[eagerPrefetch] Invalid pre-key")
            aborted = true
            break
          }
        }

        if (!aborted) {
          securityEventRecipients += recipient
        }
      }
    }

    return securityEventRecipients
  }

  data class EagerPreKeyRequest(
    val recipient: SignalServiceAddress,
    val sealedSenderAccess: SealedSenderAccess?,
    val story: Boolean
  )

  private sealed interface PreKeyFetchResult {
    data class Success(val recipient: SignalServiceAddress, val bundles: List<PreKeyBundle>) : PreKeyFetchResult
    data object Failure : PreKeyFetchResult
  }

  fun interface BatchHelper {

    /**
     * Establishes the thread local used to optimize batch updating many Recipients when their
     * identity keys change.
     *
     * When saving an identity from libsignal session creation, the save will happen, but defer
     * rotating storage id, schedule storage sync job, and updating the live recipients.
     *
     * After the [block] is finished it will then perform the deferred operations.
     */
    fun batch(block: Runnable)
  }
}
