/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data.network

import org.signal.core.util.logging.Log
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.exceptions.MalformedRequestException
import org.whispersystems.signalservice.api.push.exceptions.NoSuchSessionException
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse

sealed class RegistrationSessionResult(cause: Throwable?) : RegistrationResult(cause)

interface SessionMetadataHolder {
  fun getMetadata(): RegistrationSessionMetadataResponse
}

sealed class RegistrationSessionCreationResult(cause: Throwable?) : RegistrationSessionResult(cause) {
  companion object {

    private val TAG = Log.tag(RegistrationSessionResult::class.java)

    @JvmStatic
    fun from(networkResult: NetworkResult<RegistrationSessionMetadataResponse>): RegistrationSessionCreationResult {
      return when (networkResult) {
        is NetworkResult.Success -> {
          Success(networkResult.result)
        }

        is NetworkResult.ApplicationError -> UnknownError(networkResult.throwable)
        is NetworkResult.NetworkError -> UnknownError(networkResult.exception)
        is NetworkResult.StatusCodeError -> {
          when (val cause = networkResult.exception) {
            is RateLimitException -> createRateLimitProcessor(cause)
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

    private fun createRateLimitProcessor(exception: RateLimitException): RegistrationSessionCreationResult {
      return if (exception.retryAfterMilliseconds.isPresent) {
        RateLimited(exception, exception.retryAfterMilliseconds.get())
      } else {
        AttemptsExhausted(exception)
      }
    }
  }

  class Success(private val metadata: RegistrationSessionMetadataResponse) : RegistrationSessionCreationResult(null), SessionMetadataHolder {
    override fun getMetadata(): RegistrationSessionMetadataResponse {
      return metadata
    }
  }

  class RateLimited(cause: Throwable, val timeRemaining: Long) : RegistrationSessionCreationResult(cause)
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
          Success(networkResult.result)
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

  class Success(private val metadata: RegistrationSessionMetadataResponse) : RegistrationSessionCheckResult(null), SessionMetadataHolder {
    override fun getMetadata(): RegistrationSessionMetadataResponse {
      return metadata
    }
  }

  class SessionNotFound(cause: Throwable) : RegistrationSessionCheckResult(cause)
  class UnknownError(cause: Throwable) : RegistrationSessionCheckResult(cause)
}
