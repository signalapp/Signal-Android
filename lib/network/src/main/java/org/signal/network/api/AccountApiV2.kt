/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.signal.core.models.MasterKey
import org.signal.core.util.serialization.ByteArrayToBase64Serializer
import org.signal.core.util.serialization.ByteArrayToUrlSafeBase64Serializer
import org.signal.core.util.serialization.ECPublicKeyToBase64Serializer
import org.signal.core.util.serialization.KEMPublicKeyToBase64Serializer
import org.signal.core.util.serialization.SignalJson
import org.signal.libsignal.net.AuthAccountsService
import org.signal.libsignal.net.AuthDevicesService
import org.signal.libsignal.net.AuthUsernamesService
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.ConfirmTotpKeyError
import org.signal.libsignal.net.ConfirmedMfaKey
import org.signal.libsignal.net.GenerateTotpKeyError
import org.signal.libsignal.net.MfaKeyNotFoundException
import org.signal.libsignal.net.MfaMetadata
import org.signal.libsignal.net.PendingTotpKey
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.SvrKey
import org.signal.libsignal.net.UsernameNotAvailableException
import org.signal.libsignal.net.UsernameNotSetException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.usernames.BaseUsernameException
import org.signal.libsignal.usernames.Username
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.delete
import org.signal.network.websocket.get
import org.signal.network.websocket.put
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import kotlin.time.Duration

/**
 * Various user account specific APIs to get, update, and delete account data.
 */
class AccountApiV2(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  private val random = SecureRandom()

  /**
   * Fetch information about yourself.
   *
   * GET /v1/accounts/whoami
   * - 200: Success
   * - 401: Unauthorized
   * - 429: Rate limited
   */
  suspend fun whoAmI(): RequestResult<WhoAmIResponse, WhoAmIError> {
    return authWebSocket.requestResult(
      request = WebSocketRequestMessage.get("/v1/accounts/whoami"),
      parseSuccess = { response ->
        SignalJson.decode(WhoAmIResponse.serializer(), response.body).getOrNull()
          ?: throw IOException("Unparseable whoami response")
      },
      mapError = { response ->
        when (response.status) {
          401 -> WhoAmIError.Unauthorized
          429 -> WhoAmIError.RateLimited(response.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Sets the FCM push token the server should use to notify this device of new messages.
   */
  suspend fun setFcmToken(fcmToken: String): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthDevicesService(connection).setPushToken(fcmToken)
    }
  }

  /**
   * Removes any push tokens associated with this device. Afterwards, the server will assume this device
   * polls for new messages over an open websocket.
   */
  suspend fun clearFcmToken(): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthDevicesService(connection).clearPushToken()
    }
  }

  /**
   * Set account attributes.
   *
   * PUT /v1/accounts/attributes
   * - 200: Success
   * - 401: Unauthorized
   * - 429: Rate limited
   */
  suspend fun setAccountAttributes(accountAttributes: RegistrationApiV2.AccountAttributes): RequestResult<Unit, SetAccountAttributesError> {
    val body = SignalJson.encode(RegistrationApiV2.AccountAttributes.serializer(), accountAttributes).fold(
      ifLeft = { return RequestResult.ApplicationError(it.cause) },
      ifRight = { it }
    )

    return authWebSocket.requestResult(
      request = WebSocketRequestMessage.put("/v1/accounts/attributes", body),
      parseSuccess = { },
      mapError = { response ->
        when (response.status) {
          401 -> SetAccountAttributesError.Unauthorized
          429 -> SetAccountAttributesError.RateLimited(response.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Update the capabilities of the calling device.
   *
   * PUT /v1/devices/capabilities
   * - 200: Success
   * - 401: Unauthorized
   * - 429: Rate limited
   */
  suspend fun setCapabilities(capabilities: RegistrationApiV2.AccountAttributes.Capabilities): RequestResult<Unit, SetCapabilitiesError> {
    val body = SignalJson.encode(RegistrationApiV2.AccountAttributes.Capabilities.serializer(), capabilities).fold(
      ifLeft = { return RequestResult.ApplicationError(it.cause) },
      ifRight = { it }
    )

    return authWebSocket.requestResult(
      request = WebSocketRequestMessage.put("/v1/devices/capabilities", body),
      parseSuccess = { },
      mapError = { response ->
        when (response.status) {
          401 -> SetCapabilitiesError.Unauthorized
          429 -> SetCapabilitiesError.RateLimited(response.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Set whether this account is discoverable by phone number. Unlike [setAccountAttributes], this
   * dedicated endpoint can be called from a linked device.
   */
  suspend fun setPhoneNumberDiscoverability(discoverable: Boolean): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthAccountsService(connection).setDiscoverableByPhoneNumber(discoverable)
    }
  }

  /**
   * Enables the registration lock, deriving the lock secret from [masterKey]. While enabled, re-registering this
   * account's phone number requires proving knowledge of the secret. Only the primary device may do this.
   */
  suspend fun enableRegistrationLock(masterKey: MasterKey): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthAccountsService(connection).setRegistrationLock(SvrKey(masterKey.serialize()))
    }
  }

  /**
   * Removes any registration lock from the account. Also succeeds if no lock was set. Only the primary device may
   * do this.
   */
  suspend fun disableRegistrationLock(): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthAccountsService(connection).clearRegistrationLock()
    }
  }

  /**
   * Sets the registration recovery password, derived from [masterKey], letting this account re-register its phone number without SMS verification.
   * Any of the account's devices may do this.
   *
   * Note that we normally set the recovery password as part of [setAccountAttributes] instead.
   */
  suspend fun setRegistrationRecoveryPassword(masterKey: MasterKey): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthAccountsService(connection).setRegistrationRecoveryPassword(SvrKey(masterKey.serialize()))
    }
  }

  /**
   * Generates and stores a new pending TOTP key for the account, replacing any pending key already there. The key
   * doesn't take effect, or show up in [listMfaKeys], until [confirmTotpKey] proves the caller kept a copy of it,
   * which must happen within 24 hours. This is the only time the key material is ever handed out.
   *
   * A [TooManyTotpKeysException][org.signal.libsignal.net.TooManyTotpKeysException] or
   * [TooManyMfaKeysException][org.signal.libsignal.net.TooManyMfaKeysException] means the account is at its limit,
   * and a key has to be removed before another can be added.
   */
  suspend fun generateTotpKey(): RequestResult<PendingTotpKey, GenerateTotpKeyError> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthAccountsService(connection).generateTotpKey()
    }
  }

  /**
   * Confirms the pending TOTP key by proving a one-time password can be derived from it, and attaches [metadata] to
   * it, returning the id the service assigned. The metadata is encrypted under a key derived from [masterKey], so the
   * service never sees it.
   *
   * A [OneTimePasswordNotVerifiedException][org.signal.libsignal.net.OneTimePasswordNotVerifiedException] means the
   * password was wrong, the clocks are too far apart, or there was no pending key -- the service can't tell us which.
   */
  suspend fun confirmTotpKey(oneTimePassword: Int, metadata: MfaMetadata, masterKey: MasterKey): RequestResult<Int, ConfirmTotpKeyError> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthAccountsService(connection).confirmTotpKey(oneTimePassword = oneTimePassword, metadata = metadata, svrKey = SvrKey(masterKey.serialize()))
    }
  }

  /**
   * The confirmed MFA keys on the account. Key material is never returned, only metadata and parameters. A key whose
   * metadata can't be decrypted under [masterKey] comes back with null metadata rather than being dropped.
   */
  suspend fun listMfaKeys(masterKey: MasterKey): RequestResult<List<ConfirmedMfaKey>, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthAccountsService(connection).listMfaKeys(SvrKey(masterKey.serialize()))
    }
  }

  /**
   * Replaces the metadata attached to a confirmed MFA key, encrypted under a key derived from [masterKey].
   */
  suspend fun setMfaKeyMetadata(keyId: Int, metadata: MfaMetadata, masterKey: MasterKey): RequestResult<Unit, MfaKeyNotFoundException> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthAccountsService(connection).setMfaKeyMetadata(keyId = keyId, metadata = metadata, svrKey = SvrKey(masterKey.serialize()))
    }
  }

  /**
   * Removes an MFA key, which also succeeds when there's no key with that id, so retries look the same as the
   * first try.
   */
  suspend fun removeMfaKey(keyId: Int): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthAccountsService(connection).removeMfaKey(keyId)
    }
  }

  /**
   * Deletes the account off the service.
   *
   * DELETE /v1/accounts/me
   * - 204: Success
   * - 401: Unauthorized
   * - 429: Rate limited
   */
  suspend fun deleteAccount(): RequestResult<Unit, DeleteAccountError> {
    val result = authWebSocket.requestResult(
      request = WebSocketRequestMessage.delete("/v1/accounts/me"),
      parseSuccess = { },
      mapError = { response ->
        when (response.status) {
          401 -> DeleteAccountError.Unauthorized
          429 -> DeleteAccountError.RateLimited(response.retryAfter())
          else -> null
        }
      }
    )

    // Deleting the account invalidates the connection, which can kill the request before the response is read and
    // surface as a synthetic 4401. The deletion still went through.
    return if (result is RequestResult.RetryableNetworkError && (result.networkError as? NonSuccessfulResponseCodeException)?.code == 4401) {
      RequestResult.Success(Unit)
    } else {
      result
    }
  }

  /**
   * Generate and get an account data report. The report is returned as raw JSON.
   *
   * GET /v2/accounts/data_report
   * - 200: Success
   * - 401: Unauthorized
   * - 429: Rate limited
   */
  suspend fun accountDataReport(): RequestResult<String, AccountDataReportError> {
    return authWebSocket.requestResult(
      request = WebSocketRequestMessage.get("/v2/accounts/data_report"),
      parseSuccess = { response -> response.body },
      mapError = { response ->
        when (response.status) {
          401 -> AccountDataReportError.Unauthorized
          429 -> AccountDataReportError.RateLimited(response.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Changes the phone number that an account is associated with.
   *
   * PUT /v2/accounts/number
   * - 200: Success
   * - 403: The session was not verified or the recovery password was incorrect
   * - 409: Mismatched device ids to notify
   * - 410: Mismatched device registration ids to notify
   * - 422: Unable to parse [ChangeNumberRequest]
   * - 423: Account reglock enabled for new phone number
   * - 429: Rate limited
   */
  suspend fun changeNumber(changeNumberRequest: ChangeNumberRequest): RequestResult<ChangeNumberResponse, ChangeNumberError> {
    val body = SignalJson.encode(ChangeNumberRequest.serializer(), changeNumberRequest).fold(
      ifLeft = { return RequestResult.ApplicationError(it.cause) },
      ifRight = { it }
    )

    return authWebSocket.requestResult(
      request = WebSocketRequestMessage.put("/v2/accounts/number", body),
      parseSuccess = { response ->
        SignalJson.decode(ChangeNumberResponse.serializer(), response.body).getOrNull()
          ?: throw IOException("Unparseable change number response")
      },
      mapError = { response ->
        when (response.status) {
          403 -> ChangeNumberError.IncorrectVerification
          409 -> ChangeNumberError.MismatchedDevices(SignalJson.decode(MismatchedDevicesResponse.serializer(), response.body).getOrNull())
          410 -> ChangeNumberError.StaleDevices(SignalJson.decode(StaleDevicesResponse.serializer(), response.body).getOrNull())
          422 -> ChangeNumberError.InvalidRequest(response.body)
          423 -> ChangeNumberError.RegistrationLock(SignalJson.decode(RegistrationApiV2.RegistrationLockResponse.serializer(), response.body).getOrNull())
          429 -> ChangeNumberError.RateLimited(response.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Reserve a username for the account. This replaces an existing reservation if one exists. The username is guaranteed to be available for 5 minutes and can
   * be confirmed with [confirmUsername].
   *
   * @param usernameHashes A prioritized list of 32-byte username hashes. Must contain between 1 and 20 entries.
   * @return The hash of the reserved username. It is available for confirmation for 5 minutes. A [UsernameNotAvailableException] means none of the provided
   *   hashes were available.
   */
  suspend fun reserveUsername(usernameHashes: List<ByteArray>): RequestResult<ByteArray, UsernameNotAvailableException> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthUsernamesService(connection).reserveUsernameHash(usernameHashes)
    }
  }

  /**
   * Set a previously reserved username for the account.
   *
   * PUT /v1/accounts/username_hash/confirm
   * - 200: Success, returns the username link handle
   * - 409: Username is not reserved
   * - 410: Username unavailable
   * - 422: Unable to parse the request
   * - 429: Rate limited
   *
   * @param username The username the user wishes to confirm.
   */
  suspend fun confirmUsername(username: Username, link: Username.UsernameLink): RequestResult<UUID, ConfirmUsernameError> {
    val randomness = ByteArray(32)
    random.nextBytes(randomness)

    val proof: ByteArray = try {
      username.generateProofWithRandomness(randomness)
    } catch (e: BaseUsernameException) {
      return RequestResult.ApplicationError(e)
    }

    val body = SignalJson.encode(
      ConfirmUsernameRequestBody.serializer(),
      ConfirmUsernameRequestBody(
        usernameHash = username.hash,
        zkProof = proof,
        encryptedUsername = link.encryptedUsername
      )
    ).fold(
      ifLeft = { return RequestResult.ApplicationError(it.cause) },
      ifRight = { it }
    )

    return authWebSocket.requestResult(
      request = WebSocketRequestMessage.put("/v1/accounts/username_hash/confirm", body),
      parseSuccess = { response ->
        val parsed = SignalJson.decode(ConfirmUsernameResponseBody.serializer(), response.body).getOrNull()
          ?: throw IOException("Unparseable confirm username response")
        UUID.fromString(parsed.usernameLinkHandle)
      },
      mapError = { response ->
        when (response.status) {
          409 -> ConfirmUsernameError.UsernameNotReserved
          410 -> ConfirmUsernameError.UsernameUnavailable
          422 -> ConfirmUsernameError.InvalidRequest(response.body)
          429 -> ConfirmUsernameError.RateLimited(response.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Clears the current username hash, ciphertext, and link for the authenticated account.
   *
   * This also succeeds if the account has no username set, so a caller retrying a deletion sees
   * the same result as the original call.
   */
  suspend fun deleteUsernameHash(): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthUsernamesService(connection).deleteUsernameHash()
    }
  }

  /**
   * Creates a new username link for the given [usernameLink]. A [UsernameNotSetException] means the account has no username set.
   */
  suspend fun createUsernameLink(usernameLink: Username.UsernameLink): RequestResult<UsernameLinkComponents, UsernameNotSetException> {
    return modifyUsernameLink(usernameLink, keepLinkHandle = false)
  }

  /**
   * Updates the account's username link to the given [usernameLink], keeping the existing link handle.
   * A [UsernameNotSetException] means the account has no username set.
   */
  suspend fun updateUsernameLink(usernameLink: Username.UsernameLink): RequestResult<UsernameLinkComponents, UsernameNotSetException> {
    return modifyUsernameLink(usernameLink, keepLinkHandle = true)
  }

  /**
   * Clears any username link on the account, deactivating the link handle but leaving the username hash in place. This also succeeds if the account has no
   * username link, so a caller retrying a deletion sees the same result as the original call.
   */
  suspend fun deleteUsernameLink(): RequestResult<Unit, Nothing> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthUsernamesService(connection).deleteUsernameLink()
    }
  }

  private suspend fun modifyUsernameLink(usernameLink: Username.UsernameLink, keepLinkHandle: Boolean): RequestResult<UsernameLinkComponents, UsernameNotSetException> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthUsernamesService(connection).setUsernameLink(usernameLink.encryptedUsername, keepLinkHandle)
    }.map { UsernameLinkComponents(usernameLink.entropy, it) }
  }

  private fun <T, E : BadRequestError, R> RequestResult<T, E>.map(transform: (T) -> R): RequestResult<R, E> {
    return when (this) {
      is RequestResult.Success -> RequestResult.Success(transform(result))
      is RequestResult.NonSuccess -> this
      is RequestResult.RetryableNetworkError -> this
      is RequestResult.ApplicationError -> this
    }
  }

  @Serializable
  data class WhoAmIResponse(
    @SerialName("uuid") val aci: String,
    val pni: String? = null,
    @SerialName("number") val e164: String? = null,
    val usernameHash: String? = null,
    val entitlements: Entitlements? = null
  ) {
    @Serializable
    data class Entitlements(
      val badges: List<BadgeEntitlement> = emptyList(),
      val backup: BackupEntitlement? = null
    )

    @Serializable
    data class BadgeEntitlement(
      val id: String? = null,
      val visible: Boolean? = null,
      val expirationSeconds: Long? = null
    )

    @Serializable
    data class BackupEntitlement(
      val backupLevel: Long? = null,
      val expirationSeconds: Long? = null
    )
  }

  @Serializable
  class ChangeNumberRequest(
    val sessionId: String? = null,
    val recoveryPassword: String? = null,
    val number: String,
    @SerialName("reglock")
    val registrationLock: String? = null,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val pniIdentityKey: ByteArray,
    val deviceMessages: List<OutgoingPushMessage>,
    val devicePniSignedPrekeys: Map<String, SignedPreKeyEntity>,
    @SerialName("devicePniPqLastResortPrekeys")
    val devicePniLastResortKyberPrekeys: Map<String, KyberPreKeyEntity>,
    val pniRegistrationIds: Map<String, Int>
  )

  @Serializable
  data class ChangeNumberResponse(
    @SerialName("uuid") val aci: String,
    val pni: String? = null,
    @SerialName("number") val e164: String? = null,
    val storageCapable: Boolean = false,
    val reregistration: Boolean = false
  )

  @Serializable
  data class OutgoingPushMessage(
    val type: Int,
    val destinationDeviceId: Int,
    val destinationRegistrationId: Int,
    val content: String
  )

  @Serializable
  class SignedPreKeyEntity(
    val keyId: Long,
    @Serializable(with = ECPublicKeyToBase64Serializer::class)
    val publicKey: ECPublicKey,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val signature: ByteArray
  )

  @Serializable
  class KyberPreKeyEntity(
    val keyId: Long,
    @Serializable(with = KEMPublicKeyToBase64Serializer::class)
    val publicKey: KEMPublicKey,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val signature: ByteArray
  )

  @Serializable
  data class MismatchedDevicesResponse(
    val missingDevices: List<Int> = emptyList(),
    val extraDevices: List<Int> = emptyList()
  )

  @Serializable
  data class StaleDevicesResponse(
    val staleDevices: List<Int> = emptyList()
  )

  @Serializable
  private class ConfirmUsernameRequestBody(
    @Serializable(with = ByteArrayToUrlSafeBase64Serializer::class)
    val usernameHash: ByteArray,
    @Serializable(with = ByteArrayToUrlSafeBase64Serializer::class)
    val zkProof: ByteArray,
    @Serializable(with = ByteArrayToUrlSafeBase64Serializer::class)
    val encryptedUsername: ByteArray
  )

  @Serializable
  private class ConfirmUsernameResponseBody(
    val usernameHash: String,
    val usernameLinkHandle: String
  )

  sealed interface WhoAmIError : BadRequestError {
    data object Unauthorized : WhoAmIError
    data class RateLimited(val retryAfter: Duration?) : WhoAmIError
  }

  sealed interface SetAccountAttributesError : BadRequestError {
    data object Unauthorized : SetAccountAttributesError
    data class RateLimited(val retryAfter: Duration?) : SetAccountAttributesError
  }

  sealed interface SetCapabilitiesError : BadRequestError {
    data object Unauthorized : SetCapabilitiesError
    data class RateLimited(val retryAfter: Duration?) : SetCapabilitiesError
  }

  sealed interface DeleteAccountError : BadRequestError {
    data object Unauthorized : DeleteAccountError
    data class RateLimited(val retryAfter: Duration?) : DeleteAccountError
  }

  sealed interface AccountDataReportError : BadRequestError {
    data object Unauthorized : AccountDataReportError
    data class RateLimited(val retryAfter: Duration?) : AccountDataReportError
  }

  sealed interface ChangeNumberError : BadRequestError {
    /** The session was not verified, or the recovery password was incorrect. */
    data object IncorrectVerification : ChangeNumberError

    /** The set of devices to notify didn't match the account's devices. Null payloads mean the response body couldn't be parsed. */
    data class MismatchedDevices(val devices: MismatchedDevicesResponse?) : ChangeNumberError

    /** Some of the devices to notify had stale registration ids. Null payloads mean the response body couldn't be parsed. */
    data class StaleDevices(val devices: StaleDevicesResponse?) : ChangeNumberError

    data class InvalidRequest(val message: String) : ChangeNumberError

    /** The new number has registration lock enabled. A null payload means the response body couldn't be parsed. */
    data class RegistrationLock(val data: RegistrationApiV2.RegistrationLockResponse?) : ChangeNumberError

    data class RateLimited(val retryAfter: Duration?) : ChangeNumberError
  }

  sealed interface ConfirmUsernameError : BadRequestError {
    data object UsernameNotReserved : ConfirmUsernameError
    data object UsernameUnavailable : ConfirmUsernameError
    data class InvalidRequest(val message: String) : ConfirmUsernameError
    data class RateLimited(val retryAfter: Duration?) : ConfirmUsernameError
  }
}
