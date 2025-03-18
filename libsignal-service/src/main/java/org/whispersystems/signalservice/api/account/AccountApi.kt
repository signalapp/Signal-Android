/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.account

import org.signal.core.util.Base64
import org.signal.core.util.Base64.encodeUrlSafeWithoutPadding
import org.signal.libsignal.usernames.BaseUsernameException
import org.signal.libsignal.usernames.Username
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.delete
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.push.ConfirmUsernameRequest
import org.whispersystems.signalservice.internal.push.ConfirmUsernameResponse
import org.whispersystems.signalservice.internal.push.GcmRegistrationId
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.push.ReserveUsernameRequest
import org.whispersystems.signalservice.internal.push.ReserveUsernameResponse
import org.whispersystems.signalservice.internal.push.SetUsernameLinkRequestBody
import org.whispersystems.signalservice.internal.push.SetUsernameLinkResponseBody
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import org.whispersystems.signalservice.internal.put
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
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
   * PUT /v1/accounts/gcm
   * - 200: Success
   */
  fun setFcmToken(fcmToken: String): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.put("/v1/accounts/gcm", GcmRegistrationId(fcmToken, true))
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * DELETE /v1/account/gcm
   * - 204: Success
   */
  fun clearFcmToken(): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.delete("/v1/accounts/gcm")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
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
   * PUT /v1/accounts/registration_lock
   * - 204: Success
   */
  fun enableRegistrationLock(registrationLock: String): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.put("/v1/accounts/registration_lock", PushServiceSocket.RegistrationLockV2(registrationLock))
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * DELETE /v1/accounts/registration_lock
   * - 204: Success
   */
  fun disableRegistrationLock(): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.delete("/v1/accounts/registration_lock")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * DELETE /v1/accounts/me
   * - 204: Success
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
   * Distributes key material to linked devices after an account becomes fully PNP capable.
   *
   * PUT /v2/accounts/phone_number_identity_key_distribution
   * - 200: Success
   * - 401: Unauthorized
   * - 403: Called from non-primary device
   * - 409: Mismatched devices
   * - 410: Registration ids do not match
   * - 422: Request is malformed
   */
  fun distributePniKeys(distributionRequest: PniKeyDistributionRequest): NetworkResult<VerifyAccountResponse> {
    val request = WebSocketRequestMessage.put("/v2/accounts/phone_number_identity_key_distribution", distributionRequest)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, VerifyAccountResponse::class)
  }

  /**
   * Reserve a username for the account. This replaces an existing reservation if one exists. The username is guaranteed to be available for 5 minutes and can
   * be confirmed with confirmUsername.
   *
   * PUT /v1/accounts/username_hash/reserve
   * - 200: Success
   * - 409: Username taken
   * - 422: Username malformed
   * - 429: Rate limited
   *
   * @param usernameHashes A list of hashed usernames encoded as web-safe base64 strings without padding. The list will have a max length of 20, and each hash will be 32 bytes.
   * @return The reserved username. It is available for confirmation for 5 minutes.
   */
  fun reserveUsername(usernameHashes: List<String>): NetworkResult<ReserveUsernameResponse> {
    val request = WebSocketRequestMessage.put("/v1/accounts/username_hash/reserve", ReserveUsernameRequest(usernameHashes))
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, ReserveUsernameResponse::class)
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
   * DELETE /v1/accounts/username_hash
   * - 204: Success
   */
  fun deleteUsername(): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.delete("/v1/accounts/username_hash")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Creates a new username link for the given [usernameLink].
   *
   * PUT /v1/accounts/username_link
   * - 200: Success
   * - 409: Username is not set
   * - 422: Invalid [SetUsernameLinkRequestBody] format
   * - 429: Rate limited
   */
  fun createUsernameLink(usernameLink: Username.UsernameLink): NetworkResult<UsernameLinkComponents> {
    return modifyUsernameLink(usernameLink, false)
  }

  /**
   * Update account username link for the given [usernameLink].
   *
   * PUT /v1/accounts/username_link
   * - 200: Success
   * - 409: Username is not set
   * - 422: Invalid [SetUsernameLinkRequestBody] format
   * - 429: Rate limited
   */
  fun updateUsernameLink(usernameLink: Username.UsernameLink): NetworkResult<UsernameLinkComponents> {
    return modifyUsernameLink(usernameLink, true)
  }

  private fun modifyUsernameLink(usernameLink: Username.UsernameLink, keepLinkHandle: Boolean): NetworkResult<UsernameLinkComponents> {
    val encryptedUsername = Base64.encodeUrlSafeWithPadding(usernameLink.encryptedUsername)
    val request = WebSocketRequestMessage.put("/v1/accounts/username_link", SetUsernameLinkRequestBody(encryptedUsername, keepLinkHandle))

    return NetworkResult.fromWebSocketRequest(authWebSocket, request, SetUsernameLinkResponseBody::class)
      .map { UsernameLinkComponents(usernameLink.entropy, it.usernameLinkHandle) }
  }
}
