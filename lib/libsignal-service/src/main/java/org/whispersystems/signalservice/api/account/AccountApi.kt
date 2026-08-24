/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.account

import kotlinx.coroutines.runBlocking
import org.signal.core.models.MasterKey
import org.signal.core.util.Base64.encodeUrlSafeWithoutPadding
import org.signal.libsignal.net.AuthAccountsService
import org.signal.libsignal.net.AuthDevicesService
import org.signal.libsignal.net.AuthUsernamesService
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.SvrKey
import org.signal.libsignal.net.UsernameNotAvailableException
import org.signal.libsignal.net.UsernameNotSetException
import org.signal.libsignal.usernames.BaseUsernameException
import org.signal.libsignal.usernames.Username
import org.signal.network.NetworkResult
import org.signal.network.rest.RestStatusCodeError
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.delete
import org.signal.network.websocket.get
import org.signal.network.websocket.put
import org.whispersystems.signalservice.api.fromWebSocketRequest
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.push.ConfirmUsernameRequest
import org.whispersystems.signalservice.internal.push.ConfirmUsernameResponse
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import java.security.SecureRandom
import java.util.UUID

/**
 * Various user account specific APIs to get, update, and delete account data.
 */
class AccountApi(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  private val random = SecureRandom()

  /**
   * Fetch information about yourself.
   *
   * GET /v1/accounts/whoami
   * - 200: Success
   */
  fun whoAmI(): NetworkResult<WhoAmIResponse> {
    val request = WebSocketRequestMessage.get("/v1/accounts/whoami")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, WhoAmIResponse::class)
  }

  /**
   * Sets the FCM push token the server should use to notify this device of new messages.
   */
  fun setFcmToken(fcmToken: String): RequestResult<Unit, Nothing> {
    return runBlocking {
      authWebSocket.runCatchingWithChatConnection { connection ->
        AuthDevicesService(connection).setPushToken(fcmToken)
      }
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
   */
  fun setAccountAttributes(accountAttributes: AccountAttributes): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.put("/v1/accounts/attributes", accountAttributes)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Update the capabilities of the calling device.
   *
   * PUT /v1/devices/capabilities
   * - 200: Success
   */
  fun setCapabilities(capabilities: AccountAttributes.Capabilities): RequestResult<Unit, RestStatusCodeError> {
    val request = WebSocketRequestMessage.put("/v1/devices/capabilities", capabilities)
    return authWebSocket.fromWebSocketRequest(request, Unit::class)
  }

  /**
   * Set whether this account is discoverable by phone number. Unlike [setAccountAttributes], this
   * dedicated endpoint can be called from a linked device.
   */
  fun setPhoneNumberDiscoverability(discoverable: Boolean): RequestResult<Unit, Nothing> {
    return runBlocking {
      authWebSocket.runCatchingWithChatConnection { connection ->
        AuthAccountsService(connection).setDiscoverableByPhoneNumber(discoverable)
      }
    }
  }

  /**
   * Enables the registration lock, deriving the lock secret from [masterKey]. While enabled, re-registering this
   * account's phone number requires proving knowledge of the secret. Only the primary device may do this.
   */
  fun enableRegistrationLock(masterKey: MasterKey): RequestResult<Unit, Nothing> {
    return runBlocking {
      authWebSocket.runCatchingWithChatConnection { connection ->
        AuthAccountsService(connection).setRegistrationLock(SvrKey(masterKey.serialize()))
      }
    }
  }

  /**
   * Removes any registration lock from the account. Also succeeds if no lock was set. Only the primary device may
   * do this.
   */
  fun disableRegistrationLock(): RequestResult<Unit, Nothing> {
    return runBlocking {
      authWebSocket.runCatchingWithChatConnection { connection ->
        AuthAccountsService(connection).clearRegistrationLock()
      }
    }
  }

  /**
   * Sets the registration recovery password, derived from [masterKey], letting this account re-register its phone number without SMS verification.
   * Any of the account's devices may do this.
   *
   * Note that we normally set the recovery password as part of [setAccountAttributes] instead.
   */
  fun setRegistrationRecoveryPassword(masterKey: MasterKey): RequestResult<Unit, Nothing> {
    return runBlocking {
      authWebSocket.runCatchingWithChatConnection { connection ->
        AuthAccountsService(connection).setRegistrationRecoveryPassword(SvrKey(masterKey.serialize()))
      }
    }
  }

  /**
   * DELETE /v1/accounts/me
   * - 204: Success
   * - 4401: Success
   */
  fun deleteAccount(): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.delete("/v1/accounts/me")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Generate and get an account data report.
   *
   * GET /v2/accounts/data_report
   * - 200: Success
   */
  fun accountDataReport(): NetworkResult<String> {
    val request = WebSocketRequestMessage.get("/v2/accounts/data_report")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, String::class)
  }

  /**
   * Changes the phone number that an account is associated with.
   *
   * PUT /v2/accounts/number
   * - 200: Success
   * - 403: No recovery password provided
   * - 409: Mismatched device ids to notify
   * - 410: Mismatched device registration ids to notify
   * - 422: Unable to parse [ChangePhoneNumberRequest]
   * - 423: Account reglock enabled for new phone number
   * - 429: Rate limited
   */
  fun changeNumber(changePhoneNumberRequest: ChangePhoneNumberRequest): NetworkResult<VerifyAccountResponse> {
    val request = WebSocketRequestMessage.put("/v2/accounts/number", changePhoneNumberRequest)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, VerifyAccountResponse::class)
  }

  /**
   * Reserve a username for the account. This replaces an existing reservation if one exists. The username is guaranteed to be available for 5 minutes and can
   * be confirmed with confirmUsername.
   *
   * @param usernameHashes A prioritized list of 32-byte username hashes. Must contain between 1 and 20 entries.
   * @return The hash of the reserved username. It is available for confirmation for 5 minutes. A [UsernameNotAvailableException] means none of the provided
   *   hashes were available.
   */
  fun reserveUsername(usernameHashes: List<ByteArray>): RequestResult<ByteArray, UsernameNotAvailableException> {
    return runBlocking {
      authWebSocket.runCatchingWithChatConnection { connection ->
        AuthUsernamesService(connection).reserveUsernameHash(usernameHashes)
      }
    }
  }

  /**
   * Set a previously reserved username for the account.
   *
   * PUT /v1/accounts/username_hash/confirm
   * - 200: Success
   * - 409: Username is not reserved
   * - 410: Username unavailable
   * - 422: Unable to parse [ConfirmUsernameRequest]
   * - 429: Rate limited
   *
   * @param username The username the user wishes to confirm.
   */
  fun confirmUsername(username: Username, link: Username.UsernameLink): NetworkResult<UUID> {
    val randomness = ByteArray(32)
    random.nextBytes(randomness)

    val proof: ByteArray = try {
      username.generateProofWithRandomness(randomness)
    } catch (e: BaseUsernameException) {
      return NetworkResult.ApplicationError(e)
    }

    val confirmUsernameRequest = ConfirmUsernameRequest(
      encodeUrlSafeWithoutPadding(username.hash),
      encodeUrlSafeWithoutPadding(proof),
      encodeUrlSafeWithoutPadding(link.encryptedUsername)
    )

    val request = WebSocketRequestMessage.put("/v1/accounts/username_hash/confirm", confirmUsernameRequest)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, ConfirmUsernameResponse::class)
      .map { it.usernameLinkHandle }
  }

  /**
   * Clears the current username hash, ciphertext, and link for the authenticated account.
   *
   * This also succeeds if the account has no username set, so a caller retrying a deletion sees
   * the same result as the original call.
   */
  fun deleteUsernameHash(): RequestResult<Unit, Nothing> {
    return runBlocking {
      authWebSocket.runCatchingWithChatConnection { connection ->
        AuthUsernamesService(connection).deleteUsernameHash()
      }
    }
  }

  /**
   * Creates a new username link for the given [usernameLink]. A [UsernameNotSetException] means the account has no username set.
   */
  fun createUsernameLink(usernameLink: Username.UsernameLink): RequestResult<UsernameLinkComponents, UsernameNotSetException> {
    return modifyUsernameLink(usernameLink, keepLinkHandle = false)
  }

  /**
   * Updates the account's username link to the given [usernameLink], keeping the existing link handle.
   * A [UsernameNotSetException] means the account has no username set.
   */
  fun updateUsernameLink(usernameLink: Username.UsernameLink): RequestResult<UsernameLinkComponents, UsernameNotSetException> {
    return modifyUsernameLink(usernameLink, keepLinkHandle = true)
  }

  /**
   * Clears any username link on the account, deactivating the link handle but leaving the username hash in place. This also succeeds if the account has no
   * username link, so a caller retrying a deletion sees the same result as the original call.
   *
   * Note that our own delete flow uses [deleteUsernameHash], which clears the link as well.
   */
  fun deleteUsernameLink(): RequestResult<Unit, Nothing> {
    return runBlocking {
      authWebSocket.runCatchingWithChatConnection { connection ->
        AuthUsernamesService(connection).deleteUsernameLink()
      }
    }
  }

  private fun modifyUsernameLink(usernameLink: Username.UsernameLink, keepLinkHandle: Boolean): RequestResult<UsernameLinkComponents, UsernameNotSetException> {
    return runBlocking {
      authWebSocket.runCatchingWithChatConnection { connection ->
        AuthUsernamesService(connection).setUsernameLink(usernameLink.encryptedUsername, keepLinkHandle)
      }.map { UsernameLinkComponents(usernameLink.entropy, it) }
    }
  }
}
