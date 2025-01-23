/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data.network

import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.exceptions.AlreadyVerifiedException
import org.whispersystems.signalservice.api.push.exceptions.ChallengeRequiredException
import org.whispersystems.signalservice.api.push.exceptions.ExternalServiceFailureException
import org.whispersystems.signalservice.api.push.exceptions.ImpossiblePhoneNumberException
import org.whispersystems.signalservice.api.push.exceptions.InvalidTransportModeException
import org.whispersystems.signalservice.api.push.exceptions.MalformedRequestException
import org.whispersystems.signalservice.api.push.exceptions.NoSuchSessionException
import org.whispersystems.signalservice.api.push.exceptions.NonNormalizedPhoneNumberException
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException
import org.whispersystems.signalservice.api.push.exceptions.RequestVerificationCodeRateLimitException
import org.whispersystems.signalservice.api.push.exceptions.SubmitVerificationCodeRateLimitException
import org.whispersystems.signalservice.api.push.exceptions.TokenNotAcceptedException
import org.whispersystems.signalservice.api.svr.Svr3Credentials
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.LockedException
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * This is a processor to map a [RegistrationSessionMetadataResponse] to all the known outcomes.
 */
sealed class VerificationCodeRequestResult(cause: Throwable?) : RegistrationResult(cause) {
  companion object {

    private val TAG = Log.tag(VerificationCodeRequestResult::class.java)

    @JvmStatic
    fun from(networkResult: NetworkResult<RegistrationSessionMetadataResponse>): VerificationCodeRequestResult {
      return when (networkResult) {
        is NetworkResult.Success -> {
          val challenges = Challenge.parse(networkResult.result.metadata.requestedInformation)
          if (challenges.isNotEmpty()) {
            Log.d(TAG, "Received \"successful\" response that contains challenges: ${challenges.joinToString { it.key }}")
            ChallengeRequired(challenges)
          } else {
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
        }

        is NetworkResult.ApplicationError -> UnknownError(networkResult.throwable)
        is NetworkResult.NetworkError -> UnknownError(networkResult.exception)
        is NetworkResult.StatusCodeError -> {
          when (val cause = networkResult.exception) {
            is ChallengeRequiredException -> createChallengeRequiredProcessor(cause.response)
            is RateLimitException -> RateLimited(cause, cause.retryAfterMilliseconds.orNull())
            is ImpossiblePhoneNumberException -> ImpossibleNumber(cause)
            is NonNormalizedPhoneNumberException -> NonNormalizedNumber(cause = cause, originalNumber = cause.originalNumber, normalizedNumber = cause.normalizedNumber)
            is TokenNotAcceptedException -> TokenNotAccepted(cause)
            is ExternalServiceFailureException -> ExternalServiceFailure(cause)
            is InvalidTransportModeException -> InvalidTransportModeFailure(cause)
            is MalformedRequestException -> MalformedRequest(cause)
            is SubmitVerificationCodeRateLimitException -> {
              SubmitVerificationCodeRateLimited(cause)
            }
            is RequestVerificationCodeRateLimitException -> {
              RequestVerificationCodeRateLimited(
                cause = cause,
                nextSmsTimestamp = cause.sessionMetadata.deriveTimestamp(delta = cause.sessionMetadata.metadata.nextSms?.seconds),
                nextCallTimestamp = cause.sessionMetadata.deriveTimestamp(delta = cause.sessionMetadata.metadata.nextCall?.seconds)
              )
            }
            is LockedException -> RegistrationLocked(cause = cause, timeRemaining = cause.timeRemaining, svr2Credentials = cause.svr2Credentials, svr3Credentials = cause.svr3Credentials)
            is NoSuchSessionException -> NoSuchSession(cause)
            is AlreadyVerifiedException -> AlreadyVerified(cause)
            else -> UnknownError(cause)
          }
        }
      }
    }

    private fun createChallengeRequiredProcessor(response: RegistrationSessionMetadataResponse): VerificationCodeRequestResult {
      return ChallengeRequired(Challenge.parse(response.metadata.requestedInformation))
    }
  }

  class Success(val sessionId: String, val nextSmsTimestamp: Duration, val nextCallTimestamp: Duration, nextVerificationAttempt: Duration, val allowedToRequestCode: Boolean, challengesRequested: List<Challenge>, val verified: Boolean) : VerificationCodeRequestResult(null)

  class ChallengeRequired(val challenges: List<Challenge>) : VerificationCodeRequestResult(null)

  class RateLimited(cause: Throwable, val timeRemaining: Long?) : VerificationCodeRequestResult(cause)

  class ImpossibleNumber(cause: Throwable) : VerificationCodeRequestResult(cause)

  class NonNormalizedNumber(cause: Throwable, val originalNumber: String, val normalizedNumber: String) : VerificationCodeRequestResult(cause)

  class TokenNotAccepted(cause: Throwable) : VerificationCodeRequestResult(cause)

  class ExternalServiceFailure(cause: Throwable) : VerificationCodeRequestResult(cause)

  class InvalidTransportModeFailure(cause: Throwable) : VerificationCodeRequestResult(cause)

  class MalformedRequest(cause: Throwable) : VerificationCodeRequestResult(cause)

  class RequestVerificationCodeRateLimited(cause: Throwable, val nextSmsTimestamp: Duration, val nextCallTimestamp: Duration) : VerificationCodeRequestResult(cause) {
    val willBeAbleToRequestAgain: Boolean = nextSmsTimestamp > 0.seconds || nextCallTimestamp > 0.seconds
    fun log(now: Duration = System.currentTimeMillis().milliseconds): String {
      val sms = if (nextSmsTimestamp > 0.seconds) {
        "${(nextSmsTimestamp - now).inWholeSeconds}s"
      } else {
        "Never"
      }

      val call = if (nextCallTimestamp > 0.seconds) {
        "${(nextCallTimestamp - now).inWholeSeconds}s"
      } else {
        "Never"
      }

      return "Request verification code rate limited! nextSms: $sms nextCall: $call"
    }
  }

  class SubmitVerificationCodeRateLimited(cause: Throwable) : VerificationCodeRequestResult(cause)

  class RegistrationLocked(cause: Throwable, val timeRemaining: Long, val svr2Credentials: AuthCredentials, val svr3Credentials: Svr3Credentials) : VerificationCodeRequestResult(cause)

  class NoSuchSession(cause: Throwable) : VerificationCodeRequestResult(cause)

  class AlreadyVerified(cause: Throwable) : VerificationCodeRequestResult(cause)

  class UnknownError(cause: Throwable) : VerificationCodeRequestResult(cause)
}
