/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.reactivex.rxjava3.core.Single
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test
import org.signal.core.util.serialization.testutil.JsonGolden
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.WebsocketResponse
import org.signal.network.websocket.post
import org.signal.network.websocket.put

/**
 * Covers the kotlinx.serialization paths added alongside the Jackson ones, so models can be migrated one at a time.
 */
class KotlinxSerializationPathTest {

  @Test
  fun `fromWebSocket parses a success body`() {
    val result = NetworkResult.fromWebSocket(Model.serializer()) {
      Single.just(WebsocketResponse(200, """{"name":"Alice","device_id":2}""", emptyMap(), false))
    }

    assertThat(result).isInstanceOf(NetworkResult.Success::class)
    assertThat((result as NetworkResult.Success).result).isEqualTo(Model("Alice", 2))
  }

  @Test
  fun `fromWebSocket turns a non-2xx into a status code error`() {
    val result = NetworkResult.fromWebSocket(Model.serializer()) {
      Single.just(WebsocketResponse(409, """{"name":"Alice","device_id":2}""", emptyMap(), false))
    }

    assertThat(result).isInstanceOf(NetworkResult.StatusCodeError::class)
    assertThat((result as NetworkResult.StatusCodeError).code).isEqualTo(409)
  }

  @Test
  fun `longPolling converter treats 204 as an error`() {
    val converter = NetworkResult.LongPollingWebSocketConverter(Model.serializer())
    val result = converter.convert(WebsocketResponse(204, "", emptyMap(), false))

    assertThat(result).isInstanceOf(NetworkResult.StatusCodeError::class)
  }

  @Test
  fun `parseJsonBody reads the error body`() {
    val error = NetworkResult.StatusCodeError<Unit>(NonSuccessfulResponseCodeException(409, "", """{"name":"Alice","device_id":2}"""))

    assertThat(error.parseJsonBody(Model.serializer())).isEqualTo(Model("Alice", 2))
  }

  @Test
  fun `parseJsonBody returns null for an unparseable body`() {
    val error = NetworkResult.StatusCodeError<Unit>(NonSuccessfulResponseCodeException(409, "", "not json"))

    assertThat(error.parseJsonBody(Model.serializer())).isNull()
  }

  @Test
  fun `parseJsonBody returns null when there is no body`() {
    val error = NetworkResult.StatusCodeError<Unit>(NonSuccessfulResponseCodeException(409, ""))

    assertThat(error.parseJsonBody(Model.serializer())).isNull()
  }

  @Test
  fun `post serializes the body and sets the content type`() {
    val request = WebSocketRequestMessage.post("/v1/thing", Model("Alice", 2), Model.serializer())

    assertThat(request.verb).isEqualTo("POST")
    assertThat(request.headers).isEqualTo(listOf("content-type:application/json"))
    JsonGolden.assertJsonEquals("""{"name":"Alice","device_id":2}""", request.body!!.utf8())
  }

  @Test
  fun `put serializes the body and sets the content type`() {
    val request = WebSocketRequestMessage.put("/v1/thing", Model("Alice", 2), Model.serializer())

    assertThat(request.verb).isEqualTo("PUT")
    assertThat(request.headers).isEqualTo(listOf("content-type:application/json"))
    JsonGolden.assertJsonEquals("""{"name":"Alice","device_id":2}""", request.body!!.utf8())
  }

  @Serializable
  private data class Model(
    val name: String,
    @SerialName("device_id") val deviceId: Int
  )
}
