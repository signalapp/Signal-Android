/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.username

import kotlinx.coroutines.runBlocking
import org.signal.core.util.Base64
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.UnauthUsernamesService
import org.signal.libsignal.net.getOrError
import org.signal.libsignal.usernames.Username
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.push.GetUsernameFromLinkResponseBody
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import java.util.UUID

/**
 * Username specific APIs related to learning service information for someone else by username.
 * For APIs to manage your own username, see [AccountApi].
 */
class UsernameApi(private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket) {

  /**
   * Gets the ACI for the given [username], if it exists. This is an unauthenticated request.
   */
  fun getAciByUsername(username: Username): RequestResult<ServiceId.ACI?, Nothing> {
    return runBlocking {
      unauthWebSocket.runWithUnauthChatConnection { chatConnection ->
        UnauthUsernamesService(chatConnection).lookUpUsernameHash(username.hash)
      }.getOrError().map { it?.let { ServiceId.ACI.fromLibSignal(it) } }
    }
  }

  /**
   * Given a link serverId, this will return the encrypted username associated with the link.
   *
   * GET /v1/accounts/username_hash/[serverId]
   * - 200: Success
   * - 400: Request must not be authenticated
   * - 404: Username link not found for server id
   * - 422: Invalid request format
   * - 429: Rate limited
   */
  fun getEncryptedUsernameFromLinkServerId(serverId: UUID): NetworkResult<ByteArray> {
    val request = WebSocketRequestMessage.get("/v1/accounts/username_link/$serverId")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, GetUsernameFromLinkResponseBody::class)
      .map { Base64.decode(it.usernameLinkEncryptedValue) }
  }
}
