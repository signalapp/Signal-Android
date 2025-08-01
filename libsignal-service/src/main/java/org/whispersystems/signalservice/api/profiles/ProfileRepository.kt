/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.profiles

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException
import kotlin.time.Duration

/**
 * Collection of high-level profile operations.
 */
class ProfileRepository(private val profileApi: ProfileApi) {

  companion object {
    private val TAG = Log.tag(ProfileRepository::class)
  }

  /**
   * Fetches all profiles in parallel, returning an overall result.
   * If we hit a rate limit exception or an unexpected runtime exception, execution halts immediately.
   */
  suspend fun <Id> fetchProfiles(requests: List<ProfileFetchRequest<Id>>): ProfileFetchResult<Id> = coroutineScope {
    val successes: MutableList<IdProfilePair<Id>> = mutableListOf()
    val unregistered: MutableList<Id> = mutableListOf()
    val retryableFailures: MutableSet<Id> = requests.map { it.id }.toMutableSet()
    val verificationFailures: MutableSet<Id> = mutableSetOf()
    var retryAfter: Duration? = null

    val mutex = Mutex()

    val tasks: List<Deferred<Any>> = requests.map { request ->
      async {
        val response: NetworkResult<SignalServiceProfileWithCredential> = if (request.serviceId is ServiceId.ACI && request.profileKey != null && request.fetchExpiringCredential) {
          profileApi
            .getVersionedProfileAndCredential(request.serviceId, request.profileKey, request.sealedSenderAccess)
            .map { SignalServiceProfileWithCredential(it.first, it.second) } // to handle nullability conversion
        } else if (request.serviceId is ServiceId.ACI && request.profileKey != null) {
          profileApi
            .getVersionedProfile(request.serviceId, request.profileKey, request.sealedSenderAccess)
            .map { SignalServiceProfileWithCredential(it, null) }
        } else {
          profileApi
            .getUnversionedProfile(request.serviceId, request.sealedSenderAccess)
            .map { SignalServiceProfileWithCredential(it, null) }
        }

        when (response) {
          is NetworkResult.Success -> mutex.withLock {
            successes += IdProfilePair(request.id, response.result)
            retryableFailures -= request.id
          }
          is NetworkResult.StatusCodeError -> {
            when (response.code) {
              404 -> mutex.withLock {
                unregistered += request.id
                retryableFailures -= request.id
              }
              429 -> {
                mutex.withLock {
                  retryAfter = response.retryAfter()
                }
                throw RateLimitException(response.code, "Hit rate limit exception! Stopping immediately. Retry-After: ${response.retryAfter()}")
              }
              else -> {
                throw response.exception
              }
            }
          }
          is NetworkResult.NetworkError -> Unit
          is NetworkResult.ApplicationError -> {
            if (response.throwable is VerificationFailedException) {
              Log.w(TAG, "Failed to verify ZK profile operation for ${request.id}. Continuing with other lookups.")
              mutex.withLock {
                verificationFailures += request.id
              }
            } else {
              mutex.withLock {
                retryableFailures -= request.id
              }
              throw response.throwable
            }
          }
        }
      }
    }

    try {
      tasks.awaitAll()
    } catch (e: Exception) {
      Log.w(TAG, "Hit an exception that caused us to end early.", e)
    }

    return@coroutineScope ProfileFetchResult(
      successes = successes,
      unregistered = unregistered.toSet(),
      retryableFailures = retryableFailures,
      verificationFailures = verificationFailures,
      retryAfter = retryAfter
    )
  }

  data class ProfileFetchResult<Id>(
    val successes: List<IdProfilePair<Id>>,
    val unregistered: Set<Id>,
    val retryableFailures: Set<Id>,
    val verificationFailures: Set<Id>,
    val retryAfter: Duration?
  )

  /**
   * @param id A user-defined identifier that will be used to identify entities in the result.
   */
  data class ProfileFetchRequest<Id>(
    val id: Id,
    val serviceId: ServiceId,
    val profileKey: ProfileKey?,
    val sealedSenderAccess: SealedSenderAccess?,
    val fetchExpiringCredential: Boolean
  )

  data class IdProfilePair<Id>(
    val id: Id,
    val profileWithCredential: SignalServiceProfileWithCredential
  )

  data class SignalServiceProfileWithCredential(
    val profile: SignalServiceProfile,
    val credential: ExpiringProfileKeyCredential?
  )
}
