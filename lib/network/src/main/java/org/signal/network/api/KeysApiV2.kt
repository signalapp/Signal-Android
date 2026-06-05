/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import kotlinx.serialization.Serializable
import org.signal.core.util.serialization.ByteArrayToBase64Serializer
import org.signal.core.util.serialization.SignalJson
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.get
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import java.io.IOException
import kotlin.time.Duration

/**
 * Prekey endpoints. Uses [RequestResult] and kotlinx-serializable DTOs; no jackson, no libsignal-service response types.
 */
class KeysApiV2(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket
) {
  /**
   * Fetch prekeys for a specific device.
   *
   * GET /v2/keys/[identifier]/[deviceId]
   * - 200: Success
   * - 401: Unauthorized
   * - 404: No keys found for address/device
   * - 429: Rate limited
   */
  suspend fun getPreKey(
    identifier: String,
    deviceId: Int,
    sealedSenderAccess: SealedSenderAccess?
  ): RequestResult<PreKeyResponse, GetPreKeysError> {
    return getPreKeysBySpecifier(identifier, deviceId.toString(), sealedSenderAccess)
  }

  /**
   * Fetch prekeys for all of the recipient's devices. (Server returns a bundle per device.)
   *
   * Wildcard device specifier: `GET /v2/keys/{identifier}/{asterisk}`
   */
  suspend fun getPreKeysForAllDevices(
    identifier: String,
    sealedSenderAccess: SealedSenderAccess?
  ): RequestResult<PreKeyResponse, GetPreKeysError> {
    return getPreKeysBySpecifier(identifier, "*", sealedSenderAccess)
  }

  private suspend fun getPreKeysBySpecifier(
    identifier: String,
    deviceSpecifier: String,
    sealedSenderAccess: SealedSenderAccess?
  ): RequestResult<PreKeyResponse, GetPreKeysError> {
    val request = WebSocketRequestMessage.get("/v2/keys/$identifier/$deviceSpecifier")

    return try {
      val response = if (sealedSenderAccess != null) {
        unauthWebSocket.requestSuspend(request, sealedSenderAccess)
      } else {
        authWebSocket.requestSuspend(request)
      }

      when (response.status) {
        200 -> SignalJson.decode(PreKeyResponse.serializer(), response.body).fold(
          ifLeft = { RequestResult.ApplicationError(it.cause) },
          ifRight = { RequestResult.Success(it) }
        )
        401 -> RequestResult.NonSuccess(GetPreKeysError.Unauthorized)
        404 -> RequestResult.NonSuccess(GetPreKeysError.NotFound)
        429 -> RequestResult.NonSuccess(GetPreKeysError.RateLimited(response.retryAfter()))
        else -> RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.status}"))
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Throwable) {
      RequestResult.ApplicationError(e)
    }
  }

  /**
   * Full prekey bundle for a recipient, including the shared identity key and one entry per device.
   * Wire format for key/signature fields is base64; [ByteArrayToBase64Serializer] handles the conversion.
   */
  @Serializable
  class PreKeyResponse(
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val identityKey: ByteArray,
    val devices: List<PreKeyResponseItem> = emptyList()
  )

  @Serializable
  data class PreKeyResponseItem(
    val deviceId: Int,
    val registrationId: Int,
    val signedPreKey: SignedPreKey? = null,
    val preKey: PreKey? = null,
    val pqPreKey: KyberPreKey? = null
  )

  @Serializable
  class PreKey(
    val keyId: Long,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val publicKey: ByteArray
  )

  @Serializable
  class SignedPreKey(
    val keyId: Long,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val publicKey: ByteArray,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val signature: ByteArray
  )

  @Serializable
  class KyberPreKey(
    val keyId: Long,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val publicKey: ByteArray,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val signature: ByteArray
  )

  sealed interface GetPreKeysError : BadRequestError {
    data object Unauthorized : GetPreKeysError
    data object NotFound : GetPreKeysError
    data class RateLimited(val retryAfter: Duration?) : GetPreKeysError
  }
}
