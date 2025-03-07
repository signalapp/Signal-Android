/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import java.io.IOException

/**
 * Bridge layer to convert [NetworkResult]s into the response data or thrown exceptions.
 */
object NetworkResultUtil {

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
          else -> throw result.exception
        }
      }
    }
  }
}
