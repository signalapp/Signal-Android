/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data.network

import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
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
sealed class RegisterAccountResult(cause: Throwable?) : RegistrationResult(cause) {
  companion object {
    fun from(networkResult: NetworkResult<RegistrationRepository.AccountRegistrationResult>): RegisterAccountResult {
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

    private fun createRateLimitProcessor(exception: RateLimitException): RegisterAccountResult {
      return if (exception.retryAfterMilliseconds.isPresent) {
        RateLimited(exception, exception.retryAfterMilliseconds.get())
      } else {
        AttemptsExhausted(exception)
      }
    }
  }
  class Success(val accountRegistrationResult: RegistrationRepository.AccountRegistrationResult) : RegisterAccountResult(null)
  class IncorrectRecoveryPassword(cause: Throwable) : RegisterAccountResult(cause)
  class AuthorizationFailed(cause: Throwable) : RegisterAccountResult(cause)
  class MalformedRequest(cause: Throwable) : RegisterAccountResult(cause)
  class ValidationError(cause: Throwable) : RegisterAccountResult(cause)
  class RateLimited(cause: Throwable, val timeRemaining: Long) : RegisterAccountResult(cause)
  class AttemptsExhausted(cause: Throwable) : RegisterAccountResult(cause)
  class RegistrationLocked(cause: Throwable, val timeRemaining: Long, val svr2Credentials: AuthCredentials?, val svr3Credentials: Svr3Credentials?) : RegisterAccountResult(cause)
  class UnknownError(cause: Throwable) : RegisterAccountResult(cause)

  class SvrNoData(cause: SvrNoDataException) : RegisterAccountResult(cause)
  class SvrWrongPin(cause: SvrWrongPinException) : RegisterAccountResult(cause) {
    val triesRemaining = cause.triesRemaining
  }
}
