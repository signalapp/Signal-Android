/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import org.signal.core.models.MasterKey
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.ConfirmedMfaKey
import org.signal.libsignal.net.MfaKeyKind
import org.signal.libsignal.net.MfaKeyNotFoundException
import org.signal.libsignal.net.MfaMetadata
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.AccountApiV2
import java.time.Instant

/**
 * Service for handling read/writes for two-factor methods on an account.
 */
class TwoFactorMethodService(private val accountApi: AccountApiV2) {

  companion object {
    private val TAG = Log.tag(TwoFactorMethodService::class)

    /** The limit the service puts on a name, which is on the encoded bytes rather than on what the user sees. */
    const val MAX_NAME_LENGTH_BYTES = MfaMetadata.NAME_MAX_LENGTH
  }

  /**
   * Get every second factor on the account.
   */
  suspend fun getMethods(masterKey: MasterKey): RequestResult<List<TwoFactorMethod>, Nothing> {
    return when (val result = accountApi.listMfaKeys(masterKey)) {
      is RequestResult.Success -> {
        RequestResult.Success(result.result.map { it.toTwoFactorMethod() })
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[getMethods] Couldn't list keys.", result.networkError)
        result
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[getMethods] Couldn't list keys.", result.cause)
        result
      }
      is RequestResult.NonSuccess -> {
        result
      }
    }
  }

  /**
   * Replaces the name on a method, which means re-encrypting its whole metadata blob and handing that back to the
   * service. So the original [createdAt] has to be provided to avoiding changing the timestamp when we rewrite it.
   */
  suspend fun setName(id: Long, name: String, createdAt: Instant, masterKey: MasterKey): RequestResult<Unit, MfaKeyNotFoundException> {
    val metadata = MfaMetadata(name = name, createdAt = createdAt)

    return when (val result = accountApi.setMfaKeyMetadata(keyId = id.toInt(), metadata = metadata, masterKey = masterKey)) {
      is RequestResult.Success -> {
        result
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[setName] Couldn't set key metadata.", result.networkError)
        result
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[setName] Couldn't set key metadata.", result.cause)
        result
      }
      is RequestResult.NonSuccess -> {
        result
      }
    }
  }

  /** Takes a method off the account. */
  suspend fun removeMethod(id: Long): RequestResult<Unit, Nothing> {
    return when (val result = accountApi.removeMfaKey(id.toInt())) {
      is RequestResult.Success -> {
        result
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[removeMethod] Couldn't remove the key.", result.networkError)
        result
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[removeMethod] Couldn't remove the key.", result.cause)
        result
      }
      is RequestResult.NonSuccess -> result
    }
  }

  private fun ConfirmedMfaKey.toTwoFactorMethod(): TwoFactorMethod {
    if (metadata == null) {
      Log.w(TAG, "Couldn't read the metadata for key $id.")
    }

    return TwoFactorMethod(
      id = id.toLong(),
      kind = kind,
      name = metadata?.name,
      createdAt = metadata?.createdAt
    )
  }

  /** A second factor registered to the account. [name] and [createdAt] are null when the metadata couldn't be read. */
  data class TwoFactorMethod(
    val id: Long,
    val kind: MfaKeyKind,
    val name: String?,
    val createdAt: Instant?
  )
}
