/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.username

import kotlinx.coroutines.runBlocking
import org.signal.core.models.ServiceId
import org.signal.libsignal.net.LookUpUsernameLinkFailure
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.UnauthUsernamesService
import org.signal.libsignal.net.getOrError
import org.signal.libsignal.usernames.Username
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.get
import java.util.UUID

/**
 * Username specific APIs related to learning service information for someone else by username.
 * For APIs to manage your own username, see [AccountApi].
 */
class UsernameApi(private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket) {

  /**
   * Gets the ACI for the given [username]. This is an unauthenticated request.
   *
   * A successful result with a null value means the username was not found on the server.
   * Other errors (network, decryption, etc.) are represented by the other [RequestResult] types.
   */
  fun getAciByUsername(username: Username): RequestResult<ServiceId.ACI?, Nothing> {
    return runBlocking {
      unauthWebSocket.runCatchingWithUnauthChatConnection { chatConnection ->
        UnauthUsernamesService(chatConnection).lookUpUsernameHash(username.hash)
      }.getOrError().map { it?.let { ServiceId.ACI.fromLibSignal(it) } }
    }
  }

  /**
   * Gets the username for a ([serverId], [entropy]) pairing from a username link. This is an unauthenticated request.
   *
   * A successful result with a null value means no username link was found for the given server ID.
   * Other errors (network, decryption, etc.) are represented by the other [RequestResult] types.
   */
  fun getDecryptedUsernameFromLinkServerIdAndEntropy(serverId: UUID, entropy: ByteArray): RequestResult<Username?, LookUpUsernameLinkFailure> {
    return runBlocking {
      unauthWebSocket.runCatchingWithUnauthChatConnection { chatConnection ->
        UnauthUsernamesService(chatConnection).lookUpUsernameLink(serverId, entropy)
      }.getOrError()
    }
  }
}
