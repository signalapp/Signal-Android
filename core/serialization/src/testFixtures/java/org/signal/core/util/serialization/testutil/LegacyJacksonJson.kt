/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.serialization.testutil

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Replicas of the two legacy Jackson `ObjectMapper` configurations in the codebase, so that tests can capture the JSON
 * a model produced before it was migrated to kotlinx.serialization.
 *
 * These exist purely to support the Jackson removal and should be deleted along with `JsonUtils` and `JsonUtil`.
 */
object LegacyJacksonJson {

  /** Mirrors `org.signal.core.util.JsonUtils`, which is used for locally persisted data. Note the enum handling. */
  val storageMapper: ObjectMapper = ObjectMapper().apply {
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
    enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
    registerKotlinModule()
  }

  /** Mirrors `org.signal.network.util.JsonUtil`, which is used for network wire formats. */
  val networkMapper: ObjectMapper = ObjectMapper().apply {
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    registerKotlinModule()
  }

  fun encodeForStorage(value: Any): String = storageMapper.writeValueAsString(value)

  fun encodeForNetwork(value: Any): String = networkMapper.writeValueAsString(value)
}
