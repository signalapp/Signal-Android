/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.media

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.serialization.json.Json
import org.junit.Assert
import org.junit.Test

class TransformPropertiesTest {

  @Test
  fun `kotlinx serialize matches legacy json keys`() {
    val properties = TransformProperties.empty()

    Assert.assertEquals(
      "{\"skipTransform\":false,\"videoTrim\":false,\"videoTrimStartTimeUs\":0,\"videoTrimEndTimeUs\":0,\"sentMediaQuality\":0,\"mp4Faststart\":false,\"videoEdited\":false}",
      Json.encodeToString(properties)
    )
  }

  @Test
  fun `kotlinx parse tolerates legacy mp4Faststart key`() {
    val json = "{\"skipTransform\":false,\"videoTrim\":false,\"videoTrimStartTimeUs\":0,\"videoTrimEndTimeUs\":0,\"videoEdited\":false,\"mp4Faststart\":true}"

    val parsed = Json.decodeFromString<TransformProperties>(json)

    Assert.assertEquals(true, parsed.mp4FastStart)
    Assert.assertEquals(false, parsed.videoTrim)
    Assert.assertEquals(false, parsed.videoEdited)
  }

  @Test
  fun `jackson serialize matches legacy json keys`() {
    val properties = TransformProperties.empty()

    val objectMapper = ObjectMapper().registerKotlinModule()
    val encoded = objectMapper.writeValueAsString(properties)

    Assert.assertEquals(
      "{\"skipTransform\":false,\"videoTrim\":false,\"videoTrimStartTimeUs\":0,\"videoTrimEndTimeUs\":0,\"sentMediaQuality\":0,\"mp4Faststart\":false,\"videoEdited\":false}",
      encoded
    )
  }

  @Test
  fun `jackson parse tolerates legacy mp4Faststart key`() {
    val json = "{\"skipTransform\":false,\"videoTrim\":false,\"videoTrimStartTimeUs\":0,\"videoTrimEndTimeUs\":0,\"videoEdited\":false,\"mp4Faststart\":true}"

    val objectMapper = ObjectMapper().registerKotlinModule()
    val parsed = objectMapper.readValue(json, TransformProperties::class.java)

    Assert.assertEquals(true, parsed.mp4FastStart)
    Assert.assertEquals(false, parsed.videoTrim)
    Assert.assertEquals(false, parsed.videoEdited)
  }
}