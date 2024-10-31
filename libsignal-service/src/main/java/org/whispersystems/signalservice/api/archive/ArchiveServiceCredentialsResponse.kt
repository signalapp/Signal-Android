/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty
import okio.IOException

/**
 * Represents the result of fetching archive credentials.
 * See [ArchiveServiceCredential].
 */
class ArchiveServiceCredentialsResponse(
  @JsonProperty
  val credentials: Map<String, List<ArchiveServiceCredential>>
) {
  companion object {
    private const val KEY_MESSAGES = "messages"
    private const val KEY_MEDIA = "media"
  }

  init {
    if (!credentials.containsKey(KEY_MESSAGES)) {
      throw IOException("Missing key '$KEY_MESSAGES'")
    }

    if (!credentials.containsKey(KEY_MEDIA)) {
      throw IOException("Missing key '$KEY_MEDIA'")
    }
  }

  val messageCredentials: List<ArchiveServiceCredential>
    get() = credentials[KEY_MESSAGES]!!

  val mediaCredentials: List<ArchiveServiceCredential>
    get() = credentials[KEY_MEDIA]!!
}
