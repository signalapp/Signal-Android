/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.groupsv2

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import java.io.IOException
import kotlin.time.Duration.Companion.days

/**
 * Allow [GroupsV2Api] to have a partial kotlin conversion by putting more kotlin friendly calls here.
 */
object GroupsV2ApiHelper {
  /**
   * Provides 7 days of credentials, which you should cache.
   *
   * GET /v1/certificate/auth/group?redemptionStartSeconds=[todaySeconds]&redemptionEndSeconds=`todaySecondsPlus7DaysOfSeconds`
   * - 200: Success
   */
  @JvmStatic
  @Throws(IOException::class)
  fun getCredentials(authWebSocket: SignalWebSocket.AuthenticatedWebSocket, todaySeconds: Long): CredentialResponse {
    val todayPlus7 = todaySeconds + 7.days.inWholeSeconds
    val request = WebSocketRequestMessage.get("/v1/certificate/auth/group?redemptionStartSeconds=$todaySeconds&redemptionEndSeconds=$todayPlus7")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, CredentialResponse::class).successOrThrow()
  }
}
