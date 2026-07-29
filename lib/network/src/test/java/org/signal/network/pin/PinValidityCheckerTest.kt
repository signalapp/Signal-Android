/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.pin

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test

class PinValidityCheckerTest {

  @Test
  fun `validity matches the shared test vectors`() {
    val vectors = loadVectors()

    assertThat(vectors).isNotEmpty()

    for (vector in vectors) {
      assertThat(PinValidityChecker.valid(vector.pin), "${vector.name} [${vector.pin}]").isEqualTo(vector.valid)
    }
  }

  private fun loadVectors(): List<PinValidityVector> {
    val json = checkNotNull(javaClass.classLoader.getResourceAsStream(VECTOR_RESOURCE)) { "Missing $VECTOR_RESOURCE" }
      .bufferedReader()
      .use { it.readText() }

    return Json.decodeFromString(json)
  }

  @Serializable
  private data class PinValidityVector(
    val name: String,
    val pin: String,
    val valid: Boolean
  )

  companion object {
    private const val VECTOR_RESOURCE = "data/kbs_pin_validity_vectors.json"
  }
}
