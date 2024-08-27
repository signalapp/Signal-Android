/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data.network

import org.signal.core.util.logging.Log
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse

sealed class SubmitCaptchaResult(cause: Throwable?) : RegistrationResult(cause) {
  companion object {
    private val TAG = Log.tag(SubmitCaptchaResult::class.java)

    fun from(networkResult: NetworkResult<RegistrationSessionMetadataResponse>): SubmitCaptchaResult {
      return when (networkResult) {
        is NetworkResult.Success -> Success()
        is NetworkResult.ApplicationError -> UnknownError(networkResult.throwable)
        is NetworkResult.NetworkError -> UnknownError(networkResult.exception)
        is NetworkResult.StatusCodeError -> UnknownError(networkResult.exception)
      }
    }
  }

  class Success : SubmitCaptchaResult(null)
  class ChallengeRequired(val challenges: List<String>) : SubmitCaptchaResult(null)
  class UnknownError(cause: Throwable) : SubmitCaptchaResult(cause)
}
