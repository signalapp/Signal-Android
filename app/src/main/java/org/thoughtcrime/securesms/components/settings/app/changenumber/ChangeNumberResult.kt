/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.registration.data.network.RegistrationResult
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException
import org.whispersystems.signalservice.api.push.exceptions.IncorrectRegistrationRecoveryPasswordException
import org.whispersystems.signalservice.api.push.exceptions.MalformedRequestException
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException
import org.whispersystems.signalservice.api.svr.Svr3Credentials
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.LockedException
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse

/**
 * This is a processor to map a [VerifyAccountResponse] to all the known outcomes.
 */
sealed class ChangeNumberResult(cause: Throwable?) : RegistrationResult(cause) {
  companion object {
    fun from(networkResult: NetworkResult<ChangeNumberRepository.NumberChangeResult>): ChangeNumberResult {
      return when (networkResult) {
        is NetworkResult.Success -> Success(networkResult.result)
        is NetworkResult.ApplicationError -> UnknownError(networkResult.throwable)
        is NetworkResult.NetworkError -> UnknownError(networkResult.exception)
        is NetworkResult.StatusCodeError -> {
          when (val cause = networkResult.exception) {
            is IncorrectRegistrationRecoveryPasswordException -> IncorrectRecoveryPassword(cause)
            is AuthorizationFailedException -> AuthorizationFailed(cause)
            is MalformedRequestException -> MalformedRequest(cause)
            is RateLimitException -> createRateLimitProcessor(cause)
            is LockedException -> RegistrationLocked(cause = cause, timeRemaining = cause.timeRemaining, svr2Credentials = cause.svr2Credentials, svr3Credentials = cause.svr3Credentials)
            else -> {
              if (networkResult.code == 422) {
                ValidationError(cause)
              } else {
                UnknownError(cause)
              }
            }
          }
        }
      }
    }

    private fun createRateLimitProcessor(exception: RateLimitException): ChangeNumberResult {
      return if (exception.retryAfterMilliseconds.isPresent) {
        RateLimited(exception, exception.retryAfterMilliseconds.get())
      } else {
        AttemptsExhausted(exception)
      }
    }
  }

  class Success(val numberChangeResult: ChangeNumberRepository.NumberChangeResult) : ChangeNumberResult(null)
  class IncorrectRecoveryPassword(cause: Throwable) : ChangeNumberResult(cause)
  class AuthorizationFailed(cause: Throwable) : ChangeNumberResult(cause)
  class MalformedRequest(cause: Throwable) : ChangeNumberResult(cause)
  class ValidationError(cause: Throwable) : ChangeNumberResult(cause)
  class RateLimited(cause: Throwable, val timeRemaining: Long) : ChangeNumberResult(cause)
  class AttemptsExhausted(cause: Throwable) : ChangeNumberResult(cause)
  class RegistrationLocked(cause: Throwable, val timeRemaining: Long, val svr2Credentials: AuthCredentials?, val svr3Credentials: Svr3Credentials?) : ChangeNumberResult(cause)
  class UnknownError(cause: Throwable) : ChangeNumberResult(cause)

  class SvrNoData(cause: SvrNoDataException) : ChangeNumberResult(cause)
  class SvrWrongPin(cause: SvrWrongPinException) : ChangeNumberResult(cause) {
    val triesRemaining = cause.triesRemaining
  }
}
