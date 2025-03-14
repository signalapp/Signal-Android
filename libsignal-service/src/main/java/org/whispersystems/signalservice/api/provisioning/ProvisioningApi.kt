/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.provisioning

import org.signal.core.util.Base64
import org.signal.core.util.urlEncode
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.registration.proto.RegistrationProvisionMessage
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.registration.RestoreMethodBody
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.put
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import kotlin.time.Duration.Companion.seconds

/**
 * Linked and new device provisioning endpoints.
 */
class ProvisioningApi(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  /**
   * Encrypts and sends the [registrationProvisionMessage] from the current primary (old device) to the new device over
   * the provisioning web socket identified by [deviceIdentifier].
   *
   * PUT /v1/provisioning/[deviceIdentifier]
   * - 204: Success
   * - 400: Message was too large
   * - 404: No provisioning socket for [deviceIdentifier]
   */
  fun sendReRegisterDeviceProvisioningMessage(
    deviceIdentifier: String,
    deviceKey: ECPublicKey,
    registrationProvisionMessage: RegistrationProvisionMessage
  ): NetworkResult<Unit> {
    val cipherText = PrimaryProvisioningCipher(deviceKey).encrypt(registrationProvisionMessage)

    val request = WebSocketRequestMessage.put("/v1/provisioning/${deviceIdentifier.urlEncode()}", ProvisioningMessage(Base64.encodeWithPadding(cipherText)))
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Wait for the [RestoreMethod] to be set on the server by the new device. This is a long polling operation.
   *
   * GET /v1/devices/restore_account/[token]?timeout=[timeout]
   * - 200: A request was received for the given token
   * - 204: No request given yet, may repeat call to continue waiting
   * - 400: Invalid [token] or [timeout]
   * - 429: Rate limited
   */
  fun waitForRestoreMethod(token: String, timeout: Int = 30): NetworkResult<RestoreMethod> {
    val request = WebSocketRequestMessage.get("/v1/devices/restore_account/${token.urlEncode()}?timeout=$timeout")

    return NetworkResult.fromWebSocket(NetworkResult.LongPollingWebSocketConverter(RestoreMethodBody::class)) {
      authWebSocket.request(request, timeout.seconds)
    }.map {
      it.method ?: RestoreMethod.DECLINE
    }
  }
}
