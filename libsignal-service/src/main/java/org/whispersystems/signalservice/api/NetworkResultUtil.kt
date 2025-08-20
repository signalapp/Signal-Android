/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException
import org.whispersystems.signalservice.internal.push.SendGroupMessageResponse
import org.whispersystems.signalservice.internal.push.SendMessageResponse
import org.whispersystems.signalservice.internal.push.exceptions.GroupMismatchedDevicesException
import org.whispersystems.signalservice.internal.push.exceptions.GroupStaleDevicesException
import org.whispersystems.signalservice.internal.push.exceptions.InAppPaymentProcessorError
import org.whispersystems.signalservice.internal.push.exceptions.InAppPaymentReceiptCredentialError
import org.whispersystems.signalservice.internal.push.exceptions.InvalidUnidentifiedAccessHeaderException
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException
import org.whispersystems.signalservice.internal.push.exceptions.PaymentsRegionException
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException
import java.io.IOException
import java.util.Optional
import kotlin.time.Duration.Companion.seconds

/**
 * Bridge layer to convert [NetworkResult]s into the response data or thrown exceptions.
 */
object NetworkResultUtil {

  /**
   * Unwraps [NetworkResult] to a basic [IOException] or [NonSuccessfulResponseCodeException]. Should only
   * be used when you don't need a specific flavor of IOException for a specific response  of any kind.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun <T> successOrThrow(result: NetworkResult<T>): T {
    return when (result) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> {
        throw when (val error = result.throwable) {
          is IOException, is RuntimeException -> error
          else -> RuntimeException(error)
        }
      }
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.StatusCodeError -> throw result.exception
    }
  }

  /**
   * Convert to a basic [IOException] or [NonSuccessfulResponseCodeException]. Should only be used when you don't
   * need a specific flavor of IOException for a specific response code.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun <T> toBasicLegacy(result: NetworkResult<T>): T {
    return when (result) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> {
        throw when (val error = result.throwable) {
          is IOException, is RuntimeException -> error
          else -> RuntimeException(error)
        }
      }
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          401, 403 -> throw AuthorizationFailedException(result.code, "Authorization failed!")
          413, 429 -> throw RateLimitException(result.code, "Rate Limited", Optional.ofNullable(result.header("retry-after")?.toLongOrNull()?.seconds?.inWholeMilliseconds))
          else -> throw result.exception
        }
      }
    }
  }

  /**
   * Convert [NetworkResult] into expected type exceptions for an individual message send.
   */
  @JvmStatic
  @Throws(
    AuthorizationFailedException::class,
    UnregisteredUserException::class,
    MismatchedDevicesException::class,
    StaleDevicesException::class,
    ProofRequiredException::class,
    WebSocketUnavailableException::class,
    ServerRejectedException::class,
    IOException::class
  )
  fun toMessageSendLegacy(destination: String, result: NetworkResult<SendMessageResponse>): SendMessageResponse {
    return when (result) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> {
        throw when (val error = result.throwable) {
          is IOException, is RuntimeException -> error
          else -> RuntimeException(error)
        }
      }
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.StatusCodeError -> {
        throw when (result.code) {
          401 -> AuthorizationFailedException(result.code, "Authorization failed!")
          404 -> UnregisteredUserException(destination, result.exception)
          409 -> MismatchedDevicesException(result.parseJsonBody())
          410 -> StaleDevicesException(result.parseJsonBody())
          413, 429 -> RateLimitException(result.code, "Rate Limited", Optional.ofNullable(result.header("retry-after")?.toLongOrNull()?.seconds?.inWholeMilliseconds))
          428 -> ProofRequiredException(result.parseJsonBody(), result.header("retry-after")?.toLongOrNull() ?: -1)
          508 -> ServerRejectedException()
          else -> result.exception
        }
      }
    }
  }

  /**
   * Convert [NetworkResult] into expected type exceptions for a multi-recipient message send.
   */
  @JvmStatic
  @Throws(
    InvalidUnidentifiedAccessHeaderException::class,
    NotFoundException::class,
    GroupMismatchedDevicesException::class,
    GroupStaleDevicesException::class,
    RateLimitException::class,
    ServerRejectedException::class,
    WebSocketUnavailableException::class,
    IOException::class
  )
  fun toGroupMessageSendLegacy(result: NetworkResult<SendGroupMessageResponse>): SendGroupMessageResponse {
    return when (result) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> {
        throw when (val error = result.throwable) {
          is IOException, is RuntimeException -> error
          else -> RuntimeException(error)
        }
      }
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.StatusCodeError -> {
        throw when (result.code) {
          401 -> InvalidUnidentifiedAccessHeaderException()
          404 -> NotFoundException("At least one unregistered user is message send.")
          409 -> GroupMismatchedDevicesException(result.parseJsonBody())
          410 -> GroupStaleDevicesException(result.parseJsonBody())
          413, 429 -> throw RateLimitException(result.code, "Rate Limited", Optional.ofNullable(result.header("retry-after")?.toLongOrNull()?.seconds?.inWholeMilliseconds))
          508 -> ServerRejectedException()
          else -> result.exception
        }
      }
    }
  }

  @JvmStatic
  @Throws(IOException::class)
  fun <T> toPreKeysLegacy(result: NetworkResult<T>): T {
    return when (result) {
      is NetworkResult.Success -> result.result
      is NetworkResult.StatusCodeError -> {
        throw when (result.code) {
          400, 401 -> AuthorizationFailedException(result.code, "Authorization failed!")
          404 -> NotFoundException("Not found")
          429 -> RateLimitException(result.code, "Rate limit exceeded: ${result.code}", Optional.empty())
          508 -> ServerRejectedException()
          else -> result.exception
        }
      }
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.ApplicationError -> {
        throw when (val error = result.throwable) {
          is IOException, is RuntimeException -> error
          else -> RuntimeException(error)
        }
      }
    }
  }

  /**
   * Convert a [NetworkResult] into typed exceptions expected during setting the user's profile.
   */
  @JvmStatic
  @Throws(AuthorizationFailedException::class, PaymentsRegionException::class, RateLimitException::class, IOException::class)
  fun toSetProfileLegacy(result: NetworkResult<String?>): String? {
    return when (result) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> {
        throw when (val error = result.throwable) {
          is IOException, is RuntimeException -> error
          else -> RuntimeException(error)
        }
      }
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.StatusCodeError -> {
        throw when (result.code) {
          401 -> AuthorizationFailedException(result.code, "Authorization failed!")
          403 -> PaymentsRegionException(result.code)
          413, 429 -> RateLimitException(result.code, "Rate Limited", Optional.ofNullable(result.header("retry-after")?.toLongOrNull()))
          else -> result.exception
        }
      }
    }
  }

  /**
   * Convert a [NetworkResult] into typed exceptions expected during calls with IAP endpoints. Not all endpoints require
   * specific error parsing but if those errors do happen for them they'll fail to parse and get the normal status code
   * exception.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun <T> toIAPBasicLegacy(result: NetworkResult<T>): T {
    return when (result) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> {
        throw when (val error = result.throwable) {
          is IOException, is RuntimeException -> error
          else -> RuntimeException(error)
        }
      }
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.StatusCodeError -> {
        throw when (result.code) {
          402 -> result.parseJsonBody<InAppPaymentReceiptCredentialError>() ?: result.exception
          440 -> result.parseJsonBody<InAppPaymentProcessorError>() ?: result.exception
          else -> result.exception
        }
      }
    }
  }
}
