/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import okio.ByteString.Companion.encode
import org.whispersystems.signalservice.api.svr.Svr3Credentials
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Response when verifying whether we have valid SVR3 credentials.
 *
 * We use a map here because the keys are dynamic. The response looks like this:
 *
 * ```json
 * {
 *   "matches": {
 *     "username:password": {
 *       "status": "match",
 *       "shareSet": "<base64Data>"
 *     },
 *     ...
 *   }
 * }
 */
class BackupV3AuthCheckResponse(
  @JsonProperty
  private val matches: Map<String, MatchData>
) {

  /** A response that contains a valid SVR3 auth credential if one is present, else null. */
  val match: Svr3Credentials? = matches.entries.firstOrNull { it.value.isMatch }?.let {
    val credential = it.key.toAuthCredential()
    Svr3Credentials(credential.username(), credential.password(), it.value.shareSet)
  }

  /** A list of credentials that are invalid, in basic-auth format. */
  val invalid: List<String> = matches.filterValues { it.isInvalid }.keys.map { it.toBasic() }

  private fun String.toAuthCredential(): AuthCredentials {
    val firstColonIndex = this.indexOf(":")

    if (firstColonIndex < 0) {
      throw IOException("Invalid credential returned!")
    }

    val username = this.substring(0, firstColonIndex)
    val password = this.substring(firstColonIndex + 1)

    return AuthCredentials.create(username, password)
  }

  /** Server expects and returns values as <username>:<password> but we prefer the full encoded Basic auth header format */
  private fun String.toBasic(): String {
    return "Basic ${encode(StandardCharsets.ISO_8859_1).base64()}"
  }

  class MatchData(
    @JsonProperty
    val status: String,

    @JsonProperty
    @JsonDeserialize(using = ByteArrayDeserializerBase64::class)
    val shareSet: ByteArray
  ) {
    val isMatch = status == "match"
    val isInvalid = status == "invalid"
  }
}
