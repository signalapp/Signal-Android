/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.websocket

import kotlinx.serialization.SerializationStrategy
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.serialization.SignalJson
import org.signal.network.util.JsonUtil
import org.signal.network.websocket.WebSocketRequestMessage
import java.security.SecureRandom

/**
 * Create a basic GET web socket request
 */
fun WebSocketRequestMessage.Companion.get(path: String, headers: Map<String, String> = emptyMap()): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "GET",
    path = path,
    headers = headers.toHeaderList(),
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a basic POST web socket request
 */
fun WebSocketRequestMessage.Companion.post(path: String, body: Any?, headers: Map<String, String> = emptyMap()): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "POST",
    path = path,
    body = body?.let { JsonUtil.toJsonByteString(body) },
    headers = (if (body != null) listOf("content-type:application/json") else emptyList()) + headers.toHeaderList(),
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a basic POST web socket request, where the body is JSON-ified with the provided kotlinx.serialization
 * [serializer].
 */
fun <T> WebSocketRequestMessage.Companion.post(path: String, body: T, serializer: SerializationStrategy<T>, headers: Map<String, String> = emptyMap()): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "POST",
    path = path,
    body = body.toJsonByteString(serializer),
    headers = listOf("content-type:application/json") + headers.toHeaderList(),
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a basic DELETE web socket request
 */
fun WebSocketRequestMessage.Companion.delete(path: String, headers: Map<String, String> = emptyMap()): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "DELETE",
    path = path,
    headers = headers.toHeaderList(),
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a basic PUT web socket request, where body is JSON-ified.
 */
fun WebSocketRequestMessage.Companion.put(path: String, body: Any, headers: Map<String, String> = emptyMap()): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "PUT",
    path = path,
    headers = listOf("content-type:application/json") + headers.toHeaderList(),
    body = when (body) {
      is String -> body.toByteArray().toByteString()
      else -> JsonUtil.toJsonByteString(body)
    },
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a basic PUT web socket request, where the body is JSON-ified with the provided kotlinx.serialization
 * [serializer].
 */
fun <T> WebSocketRequestMessage.Companion.put(path: String, body: T, serializer: SerializationStrategy<T>, headers: Map<String, String> = emptyMap()): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "PUT",
    path = path,
    headers = listOf("content-type:application/json") + headers.toHeaderList(),
    body = body.toJsonByteString(serializer),
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a custom PUT web socket request, where body and content type header are provided by caller.
 */
fun WebSocketRequestMessage.Companion.putCustom(path: String, body: ByteArray, headers: Map<String, String>): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "PUT",
    path = path,
    headers = headers.toHeaderList(),
    body = body.toByteString(),
    id = SecureRandom().nextLong()
  )
}

private fun Map<String, String>.toHeaderList(): List<String> {
  return map { (key, value) -> "$key:$value" }
}

private fun <T> T.toJsonByteString(serializer: SerializationStrategy<T>): ByteString {
  return SignalJson.json.encodeToString(serializer, this).toByteArray().toByteString()
}
