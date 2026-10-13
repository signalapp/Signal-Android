/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import org.signal.core.util.UsernameUtil
import org.signal.core.util.censor
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RetryLaterException
import org.signal.libsignal.usernames.BaseUsernameException
import org.signal.libsignal.usernames.Username
import org.signal.network.api.AccountApiV2
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.util.Usernames
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

/**
 * Higher-level operations for reserving and confirming a username on the service, wrapping [AccountApiV2] and
 * handling the candidate generation, hashing, and link creation that surround the raw endpoints. Callers are
 * expected to persist any confirmed username themselves.
 */
class UsernameService(private val accountApi: AccountApiV2) {

  companion object {
    private val TAG = Log.tag(UsernameService::class)
  }

  /**
   * Reserves a username composed of [nickname] plus a numeric discriminator. If [discriminator] is provided, only that
   * exact username is attempted; otherwise the service picks from a set of generated candidates. The service holds
   * the reservation for a short time (~5 minutes), during which it can be finalized via [confirmUsername]. Reserving
   * again replaces any previous reservation.
   *
   * `PUT /v1/accounts/username_hash/reserve`
   */
  suspend fun reserveUsername(nickname: String, discriminator: String? = null): RequestResult<Username, ReserveUsernameError> {
    val candidates: List<Username> = try {
      if (discriminator == null) {
        Username.candidatesFrom(nickname, UsernameUtil.MIN_NICKNAME_LENGTH, UsernameUtil.MAX_NICKNAME_LENGTH)
      } else {
        listOf(Username("$nickname${Usernames.DELIMITER}$discriminator"))
      }
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[reserveUsername] Failed to generate candidates.", e)
      return RequestResult.NonSuccess(ReserveUsernameError.NicknameInvalid)
    }

    val hashes: List<ByteArray> = candidates.map { it.hash }

    return when (val result = accountApi.reserveUsername(hashes)) {
      is RequestResult.Success -> {
        val reserved = candidates.firstOrNull { it.hash.contentEquals(result.result) }
        if (reserved == null) {
          Log.w(TAG, "[reserveUsername] The reserved hash was not one of our candidates.")
          RequestResult.NonSuccess(ReserveUsernameError.NicknameInvalid)
        } else {
          Log.i(TAG, "[reserveUsername] Successfully reserved a username.")
          RequestResult.Success(reserved)
        }
      }
      is RequestResult.NonSuccess -> {
        Log.w(TAG, "[reserveUsername] None of the candidates were available.")
        RequestResult.NonSuccess(ReserveUsernameError.NotAvailable)
      }
      is RequestResult.RetryableNetworkError -> {
        val networkError = result.networkError
        if (networkError is RetryLaterException) {
          Log.w(TAG, "[reserveUsername] Rate limited.")
          RequestResult.NonSuccess(ReserveUsernameError.RateLimited(networkError.duration.toKotlinDuration()))
        } else {
          RequestResult.RetryableNetworkError(networkError)
        }
      }
      is RequestResult.ApplicationError -> RequestResult.ApplicationError(result.cause)
    }
  }

  /**
   * Confirms a reservation previously made via [reserveUsername], assigning the username to the account and creating
   * a new username link for it. Nothing is persisted locally.
   *
   * `PUT /v1/accounts/username_hash/confirm`
   */
  suspend fun confirmUsername(username: Username): RequestResult<ConfirmedUsername, ConfirmUsernameError> {
    val link: Username.UsernameLink = try {
      username.generateLink()
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[confirmUsername] Failed to generate the username link.", e)
      return RequestResult.NonSuccess(ConfirmUsernameError.GenerationFailed)
    }

    return when (val result = accountApi.confirmUsername(username, link)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[confirmUsername] Successfully confirmed the username.")
        RequestResult.Success(ConfirmedUsername(username, UsernameLinkComponents(link.entropy, result.result)))
      }
      is RequestResult.NonSuccess -> when (val error = result.error) {
        is AccountApiV2.ConfirmUsernameError.UsernameNotReserved -> {
          Log.w(TAG, "[confirmUsername] The username was not reserved.")
          RequestResult.NonSuccess(ConfirmUsernameError.ReservationInvalid)
        }
        is AccountApiV2.ConfirmUsernameError.UsernameUnavailable -> {
          Log.w(TAG, "[confirmUsername] The username is no longer available.")
          RequestResult.NonSuccess(ConfirmUsernameError.NotAvailable)
        }
        is AccountApiV2.ConfirmUsernameError.InvalidRequest -> {
          Log.w(TAG, "[confirmUsername] The service could not parse the request.")
          RequestResult.NonSuccess(ConfirmUsernameError.BadRequest)
        }
        is AccountApiV2.ConfirmUsernameError.RateLimited -> {
          Log.w(TAG, "[confirmUsername] Rate limited.")
          RequestResult.NonSuccess(ConfirmUsernameError.RateLimited(error.retryAfter))
        }
      }
      is RequestResult.RetryableNetworkError -> RequestResult.RetryableNetworkError(result.networkError)
      is RequestResult.ApplicationError -> {
        val cause = result.cause
        if (cause is BaseUsernameException) {
          Log.w(TAG, "[confirmUsername] Failed to generate the username proof.", cause)
          RequestResult.NonSuccess(ConfirmUsernameError.GenerationFailed)
        } else {
          RequestResult.ApplicationError(cause)
        }
      }
    }
  }

  /** A username that has been assigned to the account, along with the components of its shareable link. */
  data class ConfirmedUsername(val username: Username, val link: UsernameLinkComponents) {
    override fun toString(): String = "ConfirmedUsername(username=${username.username.censor()}, link=xxx)"
  }

  sealed interface ReserveUsernameError : BadRequestError {
    /** The nickname could not produce any valid username candidates. */
    data object NicknameInvalid : ReserveUsernameError

    /** None of the candidate usernames generated for the nickname were available. */
    data object NotAvailable : ReserveUsernameError

    data class RateLimited(val retryAfter: Duration?) : ReserveUsernameError
  }

  sealed interface ConfirmUsernameError : BadRequestError {
    /** The service has no record of the reservation (HTTP 409) -- it lapsed or was never made. */
    data object ReservationInvalid : ConfirmUsernameError

    /** The username was claimed by someone else after it was reserved (HTTP 410). */
    data object NotAvailable : ConfirmUsernameError

    /** The service could not parse the request (HTTP 422). Deterministic -- retrying will not help. */
    data object BadRequest : ConfirmUsernameError

    /** The client failed to locally generate the username, its link, or its proof. Deterministic -- retrying will not help. */
    data object GenerationFailed : ConfirmUsernameError

    data class RateLimited(val retryAfter: Duration?) : ConfirmUsernameError
  }
}
