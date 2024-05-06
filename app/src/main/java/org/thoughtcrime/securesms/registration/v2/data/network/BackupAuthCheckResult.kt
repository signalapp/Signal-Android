/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.data.network

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.BackupAuthCheckResponse

/**
 * This is a processor to map a [BackupAuthCheckResponse] to all the known outcomes.
 */
sealed class BackupAuthCheckResult(cause: Throwable?) : RegistrationResult(cause) {
  companion object {
    @JvmStatic
    fun from(networkResult: NetworkResult<BackupAuthCheckResponse>): BackupAuthCheckResult {
      return when (networkResult) {
        is NetworkResult.Success -> {
          val match = networkResult.result.match
          if (match != null) {
            SuccessWithCredentials(match)
          } else {
            SuccessWithoutCredentials()
          }
        }

        is NetworkResult.ApplicationError -> UnknownError(networkResult.throwable)
        is NetworkResult.NetworkError -> UnknownError(networkResult.exception)
        is NetworkResult.StatusCodeError -> UnknownError(networkResult.exception)
      }
    }
  }

  class SuccessWithCredentials(val authCredentials: AuthCredentials) : BackupAuthCheckResult(null)

  class SuccessWithoutCredentials : BackupAuthCheckResult(null)

  class UnknownError(cause: Throwable) : BackupAuthCheckResult(cause)
}
