/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.VisibleForTesting
import org.signal.core.util.serialization.ByteArrayToBase64NoPaddingSerializer
import org.signal.core.util.serialization.ByteArrayToBase64Serializer
import org.signal.core.util.serialization.SignalJson
import org.signal.core.util.toByteArray
import org.signal.libsignal.net.AuthKeysService
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.PreKeyCounts
import org.signal.libsignal.net.PublicEcPreKey
import org.signal.libsignal.net.PublicKemPreKey
import org.signal.libsignal.net.PublicSignedEcPreKey
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ServiceId
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.network.crypto.SealedSenderAccess
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.get
import org.signal.network.websocket.post
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import java.io.IOException
import java.security.MessageDigest
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

  /**
   * Fetches the approximate number of unused one-time prekeys the service is storing for this device, broken down by identity and key kind.
   */
  suspend fun getPreKeyCounts(): RequestResult<PreKeyCounts, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthKeysService(connection).getPreKeyCount()
    }
  }

  /**
   * Checks whether the service's view of our repeated-use prekeys matches ours. It's all-or-nothing: the service can't say what differs.
   *
   * POST /v2/keys/check
   * - 200: Everything matches
   * - 400: Bad request
   * - 401: Auth failure
   * - 409: Something doesn't match
   */
  suspend fun checkRepeatedUseKeysConsistency(
    serviceIdType: ServiceId.Kind,
    identityKey: IdentityKey,
    signedPreKeyId: Int,
    signedPreKey: ECPublicKey,
    lastResortKyberKeyId: Int,
    lastResortKyberKey: KEMPublicKey
  ): RequestResult<Unit, CheckRepeatedUseKeysError> {
    val digest: ByteArray = MessageDigest.getInstance("SHA-256").run {
      update(identityKey.serialize())
      update(signedPreKeyId.toLong().toByteArray())
      update(signedPreKey.serialize())
      update(lastResortKyberKeyId.toLong().toByteArray())
      update(lastResortKyberKey.serialize())
      digest()
    }

    val identityType = when (serviceIdType) {
      ServiceId.Kind.ACI -> "ACI"
      ServiceId.Kind.PNI -> "PNI"
    }

    return authWebSocket.requestResult(
      request = WebSocketRequestMessage.post("/v2/keys/check", CheckRepeatedUseKeysRequest(identityType, digest), CheckRepeatedUseKeysRequest.serializer()),
      parseSuccess = { },
      mapError = { response ->
        when (response.status) {
          409 -> CheckRepeatedUseKeysError.Mismatch
          else -> CheckRepeatedUseKeysError.UnknownError(response.status)
        }
      }
    )
  }

  /**
   * Replaces this device's one-time EC prekeys for [serviceIdType] with [preKeys], which must contain between 1 and 100 keys.
   */
  suspend fun setOneTimeEcPreKeys(serviceIdType: ServiceId.Kind, preKeys: List<PreKeyRecord>): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthKeysService(connection).setOneTimeEcPreKeys(serviceIdType, preKeys.map { PublicEcPreKey(it) })
    }
  }

  /**
   * Replaces this device's one-time KEM prekeys for [serviceIdType] with [preKeys], which must contain between 1 and 100 keys.
   */
  suspend fun setOneTimeKemPreKeys(serviceIdType: ServiceId.Kind, preKeys: List<KyberPreKeyRecord>): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthKeysService(connection).setOneTimeKemPreKeys(serviceIdType, preKeys.map { PublicKemPreKey(it) })
    }
  }

  /**
   * Replaces this device's signed EC prekey for [serviceIdType].
   */
  suspend fun setSignedEcPreKey(serviceIdType: ServiceId.Kind, preKey: SignedPreKeyRecord): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthKeysService(connection).setSignedEcPreKey(serviceIdType, PublicSignedEcPreKey(preKey))
    }
  }

  /**
   * Replaces this device's last-resort KEM prekey for [serviceIdType].
   */
  suspend fun setLastResortKemPreKey(serviceIdType: ServiceId.Kind, preKey: KyberPreKeyRecord): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthKeysService(connection).setLastResortKemPreKey(serviceIdType, PublicKemPreKey(preKey))
    }
  }

  /**
   * A [SealedSenderAccess.Story] can't authorize a prekey fetch, so its [SealedSenderAccess.Story.preKeyAccess] is tried
   * first if there is one, and the fetch falls back to authenticated if that is missing or rejected.
   */
  private suspend fun getPreKeysBySpecifier(
    identifier: String,
    deviceSpecifier: String,
    sealedSenderAccess: SealedSenderAccess?
  ): RequestResult<PreKeyResponse, GetPreKeysError> {
    return when (sealedSenderAccess) {
      is SealedSenderAccess.Story -> {
        val preKeyAccess = sealedSenderAccess.preKeyAccess
        if (preKeyAccess != null) {
          val result = requestPreKeys(identifier, deviceSpecifier, preKeyAccess)
          if (result !is RequestResult.NonSuccess || result.error !is GetPreKeysError.Unauthorized) {
            return result
          }
        }
        requestPreKeys(identifier, deviceSpecifier, sealedSenderAccess = null)
      }
      is SealedSenderAccess.IndividualGroupSendTokenFirst,
      is SealedSenderAccess.IndividualUnidentifiedAccessFirst,
      null -> requestPreKeys(identifier, deviceSpecifier, sealedSenderAccess)
    }
  }

  @VisibleForTesting
  internal suspend fun requestPreKeys(
    identifier: String,
    deviceSpecifier: String,
    sealedSenderAccess: SealedSenderAccess?
  ): RequestResult<PreKeyResponse, GetPreKeysError> {
    val request = WebSocketRequestMessage.get("/v2/keys/$identifier/$deviceSpecifier")
    val authenticated = sealedSenderAccess == null

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
        401 -> RequestResult.NonSuccess(GetPreKeysError.Unauthorized(authenticated))
        404 -> RequestResult.NonSuccess(GetPreKeysError.NotFound(authenticated))
        429 -> RequestResult.NonSuccess(GetPreKeysError.RateLimited(response.retryAfter()))
        else -> RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.status}"))
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: CancellationException) {
      throw e
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

  @Serializable
  class CheckRepeatedUseKeysRequest(
    val identityType: String,
    @Serializable(with = ByteArrayToBase64NoPaddingSerializer::class)
    val digest: ByteArray
  )

  sealed interface CheckRepeatedUseKeysError : BadRequestError {
    data object Mismatch : CheckRepeatedUseKeysError
    data class UnknownError(val statusCode: Int) : CheckRepeatedUseKeysError
  }

  sealed interface GetPreKeysError : BadRequestError {
    data class Unauthorized(val authenticated: Boolean) : GetPreKeysError

    /** Only an [authenticated] 404 means not registered. */
    data class NotFound(val authenticated: Boolean) : GetPreKeysError
    data class RateLimited(val retryAfter: Duration?) : GetPreKeysError
  }
}
