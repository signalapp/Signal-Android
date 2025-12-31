/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.keys

import org.signal.core.util.logging.Log
import org.signal.core.util.toByteArray
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.account.PreKeyUpload
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.push.ServiceIdType
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.post
import org.whispersystems.signalservice.internal.push.CheckRepeatedUsedPreKeysRequest
import org.whispersystems.signalservice.internal.push.KyberPreKeyEntity
import org.whispersystems.signalservice.internal.push.PreKeyEntity
import org.whispersystems.signalservice.internal.push.PreKeyResponse
import org.whispersystems.signalservice.internal.push.PreKeyState
import org.whispersystems.signalservice.internal.put
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import java.io.IOException
import java.security.MessageDigest
import java.util.LinkedList

/**
 * Contains APIs for interacting with /keys endpoints on the service.
 */
class KeysApi(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket
) {

  companion object {
    private val TAG = Log.tag(KeysApi::class)
  }

  /**
   * Checks to see if our local view of our repeated-use prekeys matches the server's view. It's an all-or-nothing match, and no details can be given beyond
   * whether or not everything perfectly matches or not.
   *
   * Status codes:
   * - 200: Everything matches
   * - 409: Something doesn't match
   */
  fun checkRepeatedUseKeys(
    serviceIdType: ServiceIdType,
    identityKey: IdentityKey,
    signedPreKeyId: Int,
    signedPreKey: ECPublicKey,
    lastResortKyberKeyId: Int,
    lastResortKyberKey: KEMPublicKey
  ): NetworkResult<Unit> {
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256").apply {
      update(identityKey.serialize())
      update(signedPreKeyId.toLong().toByteArray())
      update(signedPreKey.serialize())
      update(lastResortKyberKeyId.toLong().toByteArray())
      update(lastResortKyberKey.serialize())
    }

    val body = CheckRepeatedUsedPreKeysRequest(serviceIdType.toString(), digest.digest())

    val request = WebSocketRequestMessage.post("/v2/keys/check", body)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * The server's count of currently available (eg. unused) prekeys for this user.
   *
   * GET /v2/keys?identity=[serviceIdType]
   * - 200: Success
   */
  fun getAvailablePreKeyCounts(serviceIdType: ServiceIdType): NetworkResult<OneTimePreKeyCounts> {
    val request = WebSocketRequestMessage.get("/v2/keys?identity=${serviceIdType.queryParam()}")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, OneTimePreKeyCounts::class)
  }

  /**
   * Register an identity key, signed prekey, and list of one time prekeys with the server.
   *
   * PUT /v2/keys?identity=[preKeyUpload]`.serviceIdType`
   */
  fun setPreKeys(preKeyUpload: PreKeyUpload): NetworkResult<Unit> {
    val signedPreKey: SignedPreKeyEntity? = if (preKeyUpload.signedPreKey != null) {
      SignedPreKeyEntity(
        preKeyUpload.signedPreKey.id,
        preKeyUpload.signedPreKey.keyPair.publicKey,
        preKeyUpload.signedPreKey.signature
      )
    } else {
      null
    }

    val oneTimeEcPreKeys: List<PreKeyEntity>? = if (preKeyUpload.oneTimeEcPreKeys != null) {
      preKeyUpload
        .oneTimeEcPreKeys
        .map { oneTimeEcKey: PreKeyRecord -> PreKeyEntity(oneTimeEcKey.id, oneTimeEcKey.keyPair.publicKey) }
    } else {
      null
    }

    val lastResortKyberPreKey: KyberPreKeyEntity? = if (preKeyUpload.lastResortKyberPreKey != null) {
      KyberPreKeyEntity(
        preKeyUpload.lastResortKyberPreKey.id,
        preKeyUpload.lastResortKyberPreKey.keyPair.publicKey,
        preKeyUpload.lastResortKyberPreKey.signature
      )
    } else {
      null
    }

    val oneTimeKyberPreKeys: List<KyberPreKeyEntity>? = if (preKeyUpload.oneTimeKyberPreKeys != null) {
      preKeyUpload
        .oneTimeKyberPreKeys
        .map { record -> KyberPreKeyEntity(record.id, record.keyPair.publicKey, record.signature) }
    } else {
      null
    }

    val body = PreKeyState(signedPreKey, oneTimeEcPreKeys, lastResortKyberPreKey, oneTimeKyberPreKeys)

    val request = WebSocketRequestMessage.put("/v2/keys?identity=${preKeyUpload.serviceIdType.queryParam()}", body)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Retrieves prekeys. If the specified device is the primary (i.e. deviceId 1), it will retrieve prekeys
   * for all devices. If it is not a primary, it will only contain the prekeys for that specific device.
   *
   * GET /v2/keys/[destination]`.identifier`/[deviceSpecifier]
   * - 200: Success
   * - 400: Multiple forms of authentication provided
   * - 401: No valid authentication provided
   * - 404: No keys found for address/device
   * - 429: Rate limited
   */
  fun getPreKeys(
    destination: SignalServiceAddress,
    sealedSenderAccess: SealedSenderAccess?,
    deviceId: Int
  ): NetworkResult<List<PreKeyBundle>> {
    return getPreKeysBySpecifier(destination, sealedSenderAccess, if (deviceId == 1) "*" else deviceId.toString())
  }

  /**
   * Retrieves a prekey for a specific device.
   *
   * GET /v2/keys/[destination]`.identifier`/[deviceSpecifier]
   * - 200: Success
   * - 400: Multiple forms of authentication provided
   * - 401: No valid authentication provided
   * - 404: No keys found for address/device
   * - 429: Rate limited
   */
  fun getPreKey(destination: SignalServiceAddress, deviceId: Int): NetworkResult<PreKeyBundle> {
    return getPreKeysBySpecifier(destination, null, deviceId.toString())
      .then { bundles ->
        if (bundles.isNotEmpty()) {
          NetworkResult.Success(bundles[0])
        } else {
          NetworkResult.NetworkError(IOException("No prekeys available!"))
        }
      }
  }

  /**
   * Retrieves the public identity key and available device prekeys for the specified [destination]. Results can
   * be restricted to a specific device setting the device number for [deviceSpecifier] or can get all devices by passing `*`.
   *
   * GET /v2/keys/[destination]`.identifier`/[deviceSpecifier]
   * - 200: Success
   * - 400: Multiple forms of authentication provided
   * - 401: No valid authentication provided
   * - 404: No keys found for address/device
   * - 429: Rate limited
   */
  private fun getPreKeysBySpecifier(destination: SignalServiceAddress, sealedSenderAccess: SealedSenderAccess?, deviceSpecifier: String): NetworkResult<List<PreKeyBundle>> {
    val request = WebSocketRequestMessage.get("/v2/keys/${destination.identifier}/$deviceSpecifier")
    Log.d(TAG, "Fetching prekeys for ${destination.identifier}.$deviceSpecifier, i.e. GET ${request.path}")

    val result: NetworkResult<PreKeyResponse> = NetworkResult.fromWebSocket {
      if (sealedSenderAccess != null) {
        unauthWebSocket.request(request, sealedSenderAccess)
      } else {
        authWebSocket.request(request)
      }
    }

    if (result is NetworkResult.StatusCodeError && result.code == 404) {
      return NetworkResult.NetworkError(UnregisteredUserException(destination.identifier, result.exception))
    }

    return result.map { response ->
      val bundles: MutableList<PreKeyBundle> = LinkedList()

      for (device in response.getDevices()) {
        var preKey: ECPublicKey? = null
        var signedPreKey: ECPublicKey? = null
        var signedPreKeySignature: ByteArray? = null
        var preKeyId = PreKeyBundle.NULL_PRE_KEY_ID
        var signedPreKeyId = PreKeyBundle.NULL_PRE_KEY_ID
        var kyberPreKeyId = PreKeyBundle.NULL_PRE_KEY_ID
        var kyberPreKey: KEMPublicKey? = null
        var kyberPreKeySignature: ByteArray? = null

        if (device.getSignedPreKey() != null) {
          signedPreKey = device.getSignedPreKey().publicKey
          signedPreKeyId = device.getSignedPreKey().keyId
          signedPreKeySignature = device.getSignedPreKey().signature
        } else {
          Log.w(TAG, "No signed prekey for device ${device.deviceId}! Skipping.")
          continue
        }

        if (device.getPreKey() != null) {
          preKeyId = device.getPreKey().keyId
          preKey = device.getPreKey().publicKey
        }

        if (device.getKyberPreKey() != null) {
          kyberPreKey = device.getKyberPreKey().publicKey
          kyberPreKeyId = device.getKyberPreKey().keyId
          kyberPreKeySignature = device.getKyberPreKey().signature
        } else {
          Log.w(TAG, "No kyber prekey for device ${device.deviceId}! Skipping.")
          continue
        }

        bundles.add(
          PreKeyBundle(
            device.getRegistrationId(),
            device.getDeviceId(),
            preKeyId,
            preKey,
            signedPreKeyId,
            signedPreKey,
            signedPreKeySignature,
            response.getIdentityKey(),
            kyberPreKeyId,
            kyberPreKey,
            kyberPreKeySignature
          )
        )
      }

      bundles
    }
  }
}
