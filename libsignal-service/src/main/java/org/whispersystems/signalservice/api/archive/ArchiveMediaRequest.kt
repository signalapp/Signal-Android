/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request to copy and re-encrypt media from the attachments cdn into the backup cdn.
 */
class ArchiveMediaRequest(
  @JsonProperty val sourceAttachment: SourceAttachment,
  @JsonProperty val objectLength: Int,
  @JsonProperty val mediaId: String,
  @JsonProperty val hmacKey: String,
  @JsonProperty val encryptionKey: String
) {
  class SourceAttachment(
    @JsonProperty val cdn: Int,
    @JsonProperty val key: String
  )
}
