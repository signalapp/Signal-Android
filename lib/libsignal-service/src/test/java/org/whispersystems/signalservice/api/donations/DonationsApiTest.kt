/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFalse
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.WebsocketResponse
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import kotlin.time.Duration

class DonationsApiTest {

  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket = mockk()
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket = mockk()
  private val api = DonationsApi(authWebSocket, unauthWebSocket)

  @Test
  fun `createSubscriber attaches the Donation-Permit header`() {
    val request = captureRequest()

    api.createSubscriber(SubscriberId.generate(), "permit-abc")

    assertThat(request.captured.headers).contains("Donation-Permit:permit-abc")
  }

  @Test
  fun `putSubscription omits the Donation-Permit header`() {
    val request = captureRequest()

    api.putSubscription(SubscriberId.generate())

    assertThat(request.captured.headers.any { it.startsWith("Donation-Permit") }).isFalse()
  }

  @Test
  fun `createStripeSubscriptionPaymentMethod attaches the Donation-Permit header when a permit is provided`() {
    val request = captureRequest()

    api.createStripeSubscriptionPaymentMethod(SubscriberId.generate(), "CARD", "permit-xyz")

    assertThat(request.captured.headers).contains("Donation-Permit:permit-xyz")
  }

  @Test
  fun `createStripeOneTimePaymentIntent attaches the Donation-Permit header when a permit is provided`() {
    val request = captureRequest()

    api.createStripeOneTimePaymentIntent("USD", "CARD", 500, 1, "permit-123")

    assertThat(request.captured.headers).contains("Donation-Permit:permit-123")
  }

  private fun captureRequest(): CapturingSlot<WebSocketRequestMessage> {
    val slot = slot<WebSocketRequestMessage>()
    every { unauthWebSocket.request(capture(slot), any<Duration>()) } returns Single.just(WebsocketResponse(200, "{}", emptyMap(), false))
    return slot
  }
}
