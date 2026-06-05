/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.network.NetworkResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.post
import org.signal.network.websocket.put
import org.whispersystems.signalservice.api.fromWebSocketRequest
import org.whispersystems.signalservice.api.websocket.SignalWebSocket

/**
 * Calls for requesting and submitting rate limit triggered challenges.
 */
class RateLimitChallengeApi(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  /**
   * Request a push challenge for rate limits.
   *
   * PUT /v1/challenge/push
   * - 200: Success
   * - 404: No push token available
   * - 413: Submitted non-empty body
   * - 429: Too many attempts
   */
  fun requestPushChallenge(): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.post("/v1/challenge/push", null)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Submit a push token to reset rate limits.
   *
   * PUT /v1/challenge
   * - 200: Success
   * - 428: Challenge token is invalid
   * - 429: Too many attempts
   */
  fun submitPushChallenge(challenge: String): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.put("/v1/challenge", SubmitPushChallengePayload(challenge))
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Submit a captcha token to reset rate limits.
   *
   * PUT /v1/challenge
   * - 200: Success
   * - 428: Challenge token is invalid
   * - 429: Too many attempts
   */
  fun submitCaptchaChallenge(challenge: String, token: String): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.put("/v1/challenge", SubmitRecaptchaChallengePayload(challenge, token))
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }
}

private class SubmitPushChallengePayload(@JsonProperty val challenge: String) {
  @JsonProperty
  val type: String = "rateLimitPushChallenge"
}

private class SubmitRecaptchaChallengePayload(challenge: String, recaptchaToken: String) {
  @JsonProperty
  val type: String = "captcha"

  @JsonProperty
  val token: String = challenge

  @JsonProperty
  val captcha: String = recaptchaToken
}
