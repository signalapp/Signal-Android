/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.signal.core.util.Base64
import org.signal.network.NetworkResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.get
import org.whispersystems.signalservice.api.fromWebSocketRequest
import org.whispersystems.signalservice.api.websocket.SignalWebSocket

/**
 * Endpoints to get sender certificates.
 */
class CertificateApi(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  /**
   * GET /v1/certificate/delivery
   * - 200: Success
   */
  fun getSenderCertificate(): NetworkResult<ByteArray> {
    val request = WebSocketRequestMessage.get("/v1/certificate/delivery")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, SenderCertificate::class)
      .map { it.certificate }
  }

  /**
   * GET /v1/certificate/delivery?includeE164=false
   * - 200: Success
   */
  fun getSenderCertificateForPhoneNumberPrivacy(): NetworkResult<ByteArray> {
    val request = WebSocketRequestMessage.get("/v1/certificate/delivery?includeE164=false")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, SenderCertificate::class)
      .map { it.certificate }
  }
}

private class SenderCertificate {
  @JsonProperty
  @JsonDeserialize(using = ByteArrayDeserializer::class)
  @JsonSerialize(using = ByteArraySerializer::class)
  var certificate: ByteArray = byteArrayOf()

  class ByteArraySerializer : JsonSerializer<ByteArray>() {
    override fun serialize(value: ByteArray, gen: JsonGenerator, serializers: SerializerProvider) {
      gen.writeString(Base64.encodeWithPadding(value))
    }
  }

  class ByteArrayDeserializer : JsonDeserializer<ByteArray>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByteArray {
      return Base64.decode(p.valueAsString)
    }
  }
}
