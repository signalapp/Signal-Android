/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.rest

import org.signal.libsignal.net.RequestResult
import org.signal.network.NetworkResult
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import java.io.IOException

/**
 * Bridge a [RequestResult] (returned by [SignalRestClient]) into the older [NetworkResult] surface
 * still used by most API classes. Use this during the migration off `PushServiceSocket`.
 */
fun <T> RequestResult<T, RestStatusCodeError>.toNetworkResult(): NetworkResult<T> {
  return when (this) {
    is RequestResult.Success -> NetworkResult.Success(result)
    is RequestResult.NonSuccess -> NetworkResult.StatusCodeError(error.toNonSuccessfulResponseCodeException())
    is RequestResult.RetryableNetworkError -> NetworkResult.NetworkError(networkError)
    is RequestResult.ApplicationError -> NetworkResult.ApplicationError(cause)
  }
}

/** Build a [NonSuccessfulResponseCodeException] from a [RestStatusCodeError]. */
fun RestStatusCodeError.toNonSuccessfulResponseCodeException(): NonSuccessfulResponseCodeException {
  return NonSuccessfulResponseCodeException(
    statusCode,
    "Bad response: $statusCode",
    body,
    headers
  )
}

/** Convert a [RequestResult] failure into an [IOException]-style throw, matching `NetworkResult.successOrThrow()` shape. */
@Throws(IOException::class)
fun <T> RequestResult<T, RestStatusCodeError>.successOrThrow(): T {
  return when (this) {
    is RequestResult.Success -> result
    is RequestResult.NonSuccess -> throw error.toNonSuccessfulResponseCodeException()
    is RequestResult.RetryableNetworkError -> throw networkError
    is RequestResult.ApplicationError -> when (val t = cause) {
      is IOException -> throw t
      is RuntimeException -> throw t
      else -> throw RuntimeException(t)
    }
  }
}
