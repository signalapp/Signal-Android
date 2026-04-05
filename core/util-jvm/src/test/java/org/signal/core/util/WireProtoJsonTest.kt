/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WireProtoJsonTest {

  @Test
  fun `basic string and int64 fields serialize correctly`() {
    val message = TestMessage(name = "alice", id = 42L)
    val json = message.toJson()
    val obj = Json.parseToJsonElement(json).jsonObject

    assertEquals("alice", obj["name"]!!.jsonPrimitive.content)
    assertEquals(42L, obj["id"]!!.jsonPrimitive.long)
  }

  @Test
  fun `ByteString field serializes as base64`() {
    val bytes = "hello".encodeUtf8()
    val message = TestMessage(data = bytes)
    val json = message.toJson()
    val obj = Json.parseToJsonElement(json).jsonObject

    assertEquals(bytes.base64(), obj["data"]!!.jsonPrimitive.content)
  }

  @Test
  fun `enum field serializes as name`() {
    val message = TestMessage(status = TestMessage.TestEnum.SECOND)
    val json = message.toJson()
    val obj = Json.parseToJsonElement(json).jsonObject

    assertEquals("SECOND", obj["status"]!!.jsonPrimitive.content)
  }

  @Test
  fun `nested message serializes as JSON object`() {
    val message = TestMessage(nested = TestMessage.Nested(label = "inner"))
    val json = message.toJson()
    val obj = Json.parseToJsonElement(json).jsonObject

    val nested = obj["nested"]!!.jsonObject
    assertEquals("inner", nested["label"]!!.jsonPrimitive.content)
  }

  @Test
  fun `repeated field serializes as JSON array`() {
    val message = TestMessage(tags = listOf("a", "b", "c"))
    val json = message.toJson()
    val obj = Json.parseToJsonElement(json).jsonObject

    val tags = obj["tags"]!!.jsonArray
    assertEquals(3, tags.size)
    assertEquals("a", tags[0].jsonPrimitive.content)
    assertEquals("b", tags[1].jsonPrimitive.content)
    assertEquals("c", tags[2].jsonPrimitive.content)
  }

  @Test
  fun `default values produce sensible output`() {
    val message = TestMessage()
    val json = message.toJson()
    val obj = Json.parseToJsonElement(json).jsonObject

    assertEquals("", obj["name"]!!.jsonPrimitive.content)
    assertEquals(0L, obj["id"]!!.jsonPrimitive.long)
  }

  @Test
  fun `null optional fields serialize as null`() {
    val message = TestMessage()
    val json = message.toJson()
    val obj = Json.parseToJsonElement(json).jsonObject

    assertTrue(obj["nested"] is JsonNull)
    assertTrue(obj["status"] is JsonNull)
  }

  @Test
  fun `round trip produces valid parseable JSON`() {
    val message = TestMessage(
      name = "test",
      id = 100L,
      data = "bytes".encodeUtf8(),
      status = TestMessage.TestEnum.FIRST,
      nested = TestMessage.Nested(label = "deep"),
      tags = listOf("x", "y")
    )
    val json = message.toJson()
    val obj = Json.parseToJsonElement(json).jsonObject

    assertEquals("test", obj["name"]!!.jsonPrimitive.content)
    assertEquals(100L, obj["id"]!!.jsonPrimitive.long)
    assertEquals("deep", obj["nested"]!!.jsonObject["label"]!!.jsonPrimitive.content)
    assertEquals(2, obj["tags"]!!.jsonArray.size)
  }

  @Test
  fun `unknownFields and adapter are excluded from output`() {
    val message = TestMessage(name = "test")
    val json = message.toJson()

    assertFalse(json.contains("unknownFields"))
    assertFalse(json.contains("\"adapter\""))
  }
}
