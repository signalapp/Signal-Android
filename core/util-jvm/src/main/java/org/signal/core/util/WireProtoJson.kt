/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import com.squareup.wire.Message
import com.squareup.wire.WireEnum
import com.squareup.wire.WireField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.ByteString

fun Message<*, *>.toJson(): String {
  return Json.encodeToString(JsonElement.serializer(), toJsonElement())
}

private fun Message<*, *>.toJsonElement(): JsonObject {
  val map = mutableMapOf<String, JsonElement>()

  for (field in this.javaClass.declaredFields) {
    field.getAnnotation(WireField::class.java) ?: continue
    field.isAccessible = true

    val value = field.get(this)
    map[field.name] = valueToJsonElement(value)
  }

  return JsonObject(map)
}

private fun valueToJsonElement(value: Any?): JsonElement {
  return when (value) {
    null -> JsonNull
    is Message<*, *> -> value.toJsonElement()
    is WireEnum -> JsonPrimitive((value as Enum<*>).name)
    is ByteString -> JsonPrimitive(value.base64())
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map { valueToJsonElement(it) })
    else -> JsonPrimitive(value.toString())
  }
}
