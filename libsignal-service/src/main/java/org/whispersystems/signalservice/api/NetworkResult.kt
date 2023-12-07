/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import java.io.IOException

/**
 * A helper class that wraps the result of a network request, turning common exceptions
 * into sealed classes, with optional request chaining.
 *
 * This was designed to be a middle ground between the heavy reliance on specific exceptions
 * in old network code (which doesn't translate well to kotlin not having checked exceptions)
 * and plain rx, which still doesn't free you from having to catch exceptions and translate
 * things to sealed classes yourself.
 *
 * If you have a very complicated network request with lots of different possible response types
 * based on specific errors, this isn't for you. You're likely better off writing your own
 * sealed class. However, for the majority of requests which just require getting a model from
 * the success case and the status code of the error, this can be quite convenient.
 */
sealed class NetworkResult<T> {
  companion object {
    /**
     * A convenience method to capture the common case of making a request.
     * Perform the network action in the [fetch] lambda, returning your result.
     * Common exceptions will be caught and translated to errors.
     */
    fun <T> fromFetch(fetch: () -> T): NetworkResult<T> = try {
      Success(fetch())
    } catch (e: NonSuccessfulResponseCodeException) {
      StatusCodeError(e.code, e)
    } catch (e: IOException) {
      NetworkError(e)
    } catch (e: Throwable) {
      ApplicationError(e)
    }
  }

  /** Indicates the request was successful */
  data class Success<T>(val result: T) : NetworkResult<T>()

  /** Indicates a generic network error occurred before we were able to process a response. */
  data class NetworkError<T>(val throwable: Throwable? = null) : NetworkResult<T>()

  /** Indicates we got a response, but it was a non-2xx response. */
  data class StatusCodeError<T>(val code: Int, val throwable: Throwable? = null) : NetworkResult<T>()

  /** Indicates that the application somehow failed in a way unrelated to network activity. Usually a runtime crash. */
  data class ApplicationError<T>(val throwable: Throwable) : NetworkResult<T>()

  /**
   * Returns the result if successful, otherwise turns the result back into an exception and throws it.
   */
  fun successOrThrow(): T {
    when (this) {
      is Success -> return result
      is NetworkError -> throw throwable ?: PushNetworkException("Network error")
      is StatusCodeError -> throw throwable ?: NonSuccessfulResponseCodeException(this.code)
      is ApplicationError -> throw throwable
    }
  }

  /**
   * Takes the output of one [NetworkResult] and transforms it into another if the operation is successful.
   * If it's a failure, the original failure will be propagated. Useful for changing the type of a result.
   */
  fun <R> map(transform: (T) -> R): NetworkResult<R> {
    return when (this) {
      is Success -> Success(transform(this.result))
      is NetworkError -> NetworkError(throwable)
      is StatusCodeError -> StatusCodeError(code, throwable)
      is ApplicationError -> ApplicationError(throwable)
    }
  }

  /**
   * Takes the output of one [NetworkResult] and passes it as the input to another if the operation is successful.
   * If it's a failure, the original failure will be propagated. Useful for chaining operations together.
   */
  fun <R> then(result: (T) -> NetworkResult<R>): NetworkResult<R> {
    return when (this) {
      is Success -> result(this.result)
      is NetworkError -> NetworkError(throwable)
      is StatusCodeError -> StatusCodeError(code, throwable)
      is ApplicationError -> ApplicationError(throwable)
    }
  }
}
