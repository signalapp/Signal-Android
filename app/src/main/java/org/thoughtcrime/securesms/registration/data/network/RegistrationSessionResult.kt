/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data.network

import org.signal.core.util.orNull
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.exceptions.MalformedRequestException
import org.whispersystems.signalservice.api.push.exceptions.NoSuchSessionException
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class RegistrationSessionResult(cause: Throwable?) : RegistrationResult(cause)

interface SessionMetadataResult {
  val sessionId: String
  val nextSmsTimestamp: Duration
  val nextCallTimestamp: Duration
  val nextVerificationAttempt: Duration
  val allowedToRequestCode: Boolean
  val challengesRequested: List<Challenge>
  val verified: Boolean
}

sealed class RegistrationSessionCreationResult(cause: Throwable?) : RegistrationSessionResult(cause) {
  companion object {

    @JvmStatic
    fun from(networkResult: NetworkResult<RegistrationSessionMetadataResponse>): RegistrationSessionCreationResult {
      return when (networkResult) {
        is NetworkResult.Success -> {
          Success(
            sessionId = networkResult.result.metadata.id,
            nextSmsTimestamp = networkResult.result.deriveTimestamp(delta = networkResult.result.metadata.nextSms?.seconds),
            nextCallTimestamp = networkResult.result.deriveTimestamp(delta = networkResult.result.metadata.nextCall?.seconds),
            nextVerificationAttempt = networkResult.result.deriveTimestamp(delta = networkResult.result.metadata.nextVerificationAttempt?.seconds),
            allowedToRequestCode = networkResult.result.metadata.allowedToRequestCode,
            challengesRequested = Challenge.parse(networkResult.result.metadata.requestedInformation),
            verified = networkResult.result.metadata.verified
          )
        }

        is NetworkResult.ApplicationError -> UnknownError(networkResult.throwable)
        is NetworkResult.NetworkError -> UnknownError(networkResult.exception)
        is NetworkResult.StatusCodeError -> {
          when (val cause = networkResult.exception) {
            is RateLimitException -> RateLimited(cause, cause.retryAfterMilliseconds.orNull())
            is MalformedRequestException -> MalformedRequest(cause)
            else -> if (networkResult.code == 422) {
              ServerUnableToParse(cause)
            } else {
              UnknownError(cause)
            }
          }
        }
      }
    }
  }

  class Success(
    override val sessionId: String,
    override val nextSmsTimestamp: Duration,
    override val nextCallTimestamp: Duration,
    override val nextVerificationAttempt: Duration,
    override val allowedToRequestCode: Boolean,
    override val challengesRequested: List<Challenge>,
    override val verified: Boolean
  ) : RegistrationSessionCreationResult(null), SessionMetadataResult

  class RateLimited(cause: Throwable, val timeRemaining: Long?) : RegistrationSessionCreationResult(cause)
  class AttemptsExhausted(cause: Throwable) : RegistrationSessionCreationResult(cause)
  class ServerUnableToParse(cause: Throwable) : RegistrationSessionCreationResult(cause)
  class MalformedRequest(cause: Throwable) : RegistrationSessionCreationResult(cause)
  class UnknownError(cause: Throwable) : RegistrationSessionCreationResult(cause)
}

sealed class RegistrationSessionCheckResult(cause: Throwable?) : RegistrationSessionResult(cause) {
  companion object {
    fun from(networkResult: NetworkResult<RegistrationSessionMetadataResponse>): RegistrationSessionCheckResult {
      return when (networkResult) {
        is NetworkResult.Success -> {
          Success(
            sessionId = networkResult.result.metadata.id,
            nextSmsTimestamp = networkResult.result.deriveTimestamp(delta = networkResult.result.metadata.nextSms?.seconds),
            nextCallTimestamp = networkResult.result.deriveTimestamp(delta = networkResult.result.metadata.nextCall?.seconds),
            nextVerificationAttempt = networkResult.result.deriveTimestamp(delta = networkResult.result.metadata.nextVerificationAttempt?.seconds),
            allowedToRequestCode = networkResult.result.metadata.allowedToRequestCode,
            challengesRequested = Challenge.parse(networkResult.result.metadata.requestedInformation),
            verified = networkResult.result.metadata.verified
          )
        }

        is NetworkResult.ApplicationError -> UnknownError(networkResult.throwable)
        is NetworkResult.NetworkError -> UnknownError(networkResult.exception)
        is NetworkResult.StatusCodeError -> {
          when (val cause = networkResult.exception) {
            is NoSuchSessionException, is NotFoundException -> SessionNotFound(cause)
            else -> UnknownError(cause)
          }
        }
      }
    }
  }

  class Success(
    override val sessionId: String,
    override val nextSmsTimestamp: Duration,
    override val nextCallTimestamp: Duration,
    override val nextVerificationAttempt: Duration,
    override val allowedToRequestCode: Boolean,
    override val challengesRequested: List<Challenge>,
    override val verified: Boolean
  ) : RegistrationSessionCheckResult(null), SessionMetadataResult

  class SessionNotFound(cause: Throwable) : RegistrationSessionCheckResult(cause)
  class UnknownError(cause: Throwable) : RegistrationSessionCheckResult(cause)
}
