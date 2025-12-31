/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import okio.ByteString.Companion.encode
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Verify KBS auth credentials JSON response.
 */
data class BackupV2AuthCheckResponse @JsonCreator constructor(
  private val matches: Map<String, Map<String, Any>>
) {
  private val actualMatches = matches["matches"] ?: emptyMap()

  val match: AuthCredentials? = actualMatches.entries.firstOrNull { it.value.toString() == "match" }?.key?.toAuthCredential()
  val invalid: List<String> = actualMatches.filterValues { it.toString() == "invalid" }.keys.map { it.toBasic() }

  /** Server expects and returns values as <username>:<password> but we prefer the full encoded Basic auth header format */
  private fun String.toBasic(): String {
    return "Basic ${encode(StandardCharsets.ISO_8859_1).base64()}"
  }

  private fun String.toAuthCredential(): AuthCredentials {
    val firstColonIndex = this.indexOf(":")

    if (firstColonIndex < 0) {
      throw IOException("Invalid credential returned!")
    }

    val username = this.substring(0, firstColonIndex)
    val password = this.substring(firstColonIndex + 1)

    return AuthCredentials.create(username, password)
  }

  fun merge(other: BackupV2AuthCheckResponse): BackupV2AuthCheckResponse {
    return BackupV2AuthCheckResponse(this.matches + other.matches)
  }
}
