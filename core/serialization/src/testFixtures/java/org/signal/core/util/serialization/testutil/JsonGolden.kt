/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.serialization.testutil

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Verifies that a model migrated from Jackson to kotlinx.serialization still produces and accepts the exact same JSON.
 *
 * The intended workflow when migrating a model is:
 * 1. Before touching the model, capture its current output via [LegacyJacksonJson] and paste the string into a test.
 * 2. Migrate the model to `@Serializable`.
 * 3. Assert with [assertMatchesGolden], which fails if either direction drifted.
 */
object JsonGolden {

  /**
   * Asserts that [value] encodes to JSON equivalent to [golden], and that decoding [golden] round-trips back to the
   * same JSON. Object key order is ignored; everything else must match exactly.
   */
  fun <T> assertMatchesGolden(serializer: KSerializer<T>, value: T, golden: String, json: Json) {
    val encoded = json.encodeToString(serializer, value)
    assertJsonEquals(golden, encoded, "Encoding does not match the golden JSON.")

    val reEncoded = json.encodeToString(serializer, json.decodeFromString(serializer, golden))
    assertJsonEquals(golden, reEncoded, "Decoding the golden JSON and re-encoding it does not match.")
  }

  /**
   * Asserts that two JSON strings are structurally equal, ignoring object key order.
   */
  fun assertJsonEquals(expected: String, actual: String, message: String = "JSON does not match.") {
    val expectedElement = Json.parseToJsonElement(expected)
    val actualElement = Json.parseToJsonElement(actual)

    if (expectedElement != actualElement) {
      throw AssertionError("$message\n  Expected: ${expectedElement.canonical()}\n  Actual:   ${actualElement.canonical()}")
    }
  }

  private fun JsonElement.canonical(): String = Json { prettyPrint = false }.encodeToString(JsonElement.serializer(), this)
}
