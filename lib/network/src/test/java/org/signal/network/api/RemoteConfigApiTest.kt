/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RemoteConfigApi.GetRemoteConfigError
import org.signal.network.api.RemoteConfigApi.RemoteConfigResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.WebsocketResponse
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import java.io.IOException

class RemoteConfigApiTest {

  private val authWebSocket = mockk<SignalWebSocket.AuthenticatedWebSocket>()
  private val api = RemoteConfigApi(authWebSocket)

  @Test
  fun `200 parses config, decodes booleans, and reads timestamp and etag headers`() = runTest {
    respondWith(
      status = 200,
      body = """{"config":{"a.bool":"TRUE","b.bool":"false","c.string":"hello"}}""",
      headers = mapOf("x-signal-timestamp" to "1234", "etag" to "abc")
    )

    val result = api.getRemoteConfig()

    assertThat(result).isEqualTo(
      RequestResult.Success(
        RemoteConfigResult.Updated(
          config = mapOf("a.bool" to true, "b.bool" to false, "c.string" to "hello"),
          serverEpochTimeMilliseconds = 1234,
          eTag = "abc"
        )
      )
    )
  }

  @Test
  fun `304 returns not modified with server timestamp`() = runTest {
    respondWith(status = 304, headers = mapOf("x-signal-timestamp" to "5678"))

    val result = api.getRemoteConfig(eTag = "abc")

    assertThat(result).isEqualTo(RequestResult.Success(RemoteConfigResult.NotModified(5678)))
  }

  @Test
  fun `etag is sent as If-None-Match`() = runTest {
    val request = respondWith(status = 304)

    api.getRemoteConfig(eTag = "abc")

    assertThat(request.captured.headers).isEqualTo(listOf("If-None-Match:abc"))
  }

  @Test
  fun `no etag sends no If-None-Match`() = runTest {
    val request = respondWith(status = 200, body = """{"config":{}}""")

    api.getRemoteConfig()

    assertThat(request.captured.headers).isEqualTo(emptyList())
  }

  @Test
  fun `missing etag header yields null etag`() = runTest {
    respondWith(status = 200, body = """{"config":{}}""")

    val result = api.getRemoteConfig() as RequestResult.Success

    assertThat((result.result as RemoteConfigResult.Updated).eTag).isNull()
  }

  @Test
  fun `401 returns unauthorized`() = runTest {
    respondWith(status = 401)

    assertThat(api.getRemoteConfig()).isEqualTo(RequestResult.NonSuccess(GetRemoteConfigError.Unauthorized))
  }

  @Test
  fun `5xx returns retryable network error`() = runTest {
    respondWith(status = 500)

    assertThat(api.getRemoteConfig()).isInstanceOf<RequestResult.RetryableNetworkError>()
  }

  @Test
  fun `unexpected status returns retryable network error`() = runTest {
    respondWith(status = 404)

    assertThat(api.getRemoteConfig()).isInstanceOf<RequestResult.RetryableNetworkError>()
  }

  @Test
  fun `malformed body returns retryable network error`() = runTest {
    respondWith(status = 200, body = "not json")

    assertThat(api.getRemoteConfig()).isInstanceOf<RequestResult.RetryableNetworkError>()
  }

  @Test
  fun `io exception returns retryable network error`() = runTest {
    coEvery { authWebSocket.requestSuspend(any(), any()) } throws IOException()

    assertThat(api.getRemoteConfig()).isInstanceOf<RequestResult.RetryableNetworkError>()
  }

  private fun respondWith(status: Int, body: String = "", headers: Map<String, String> = emptyMap()): CapturingSlot<WebSocketRequestMessage> {
    val request = slot<WebSocketRequestMessage>()
    coEvery { authWebSocket.requestSuspend(capture(request), any()) } returns WebsocketResponse(status, body, headers, false)
    return request
  }
}
