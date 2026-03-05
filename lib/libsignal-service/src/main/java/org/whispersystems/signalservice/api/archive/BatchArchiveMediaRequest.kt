/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request to copy and re-encrypt media from the attachments cdn into the backup cdn.
 */
class BatchArchiveMediaRequest(
  @JsonProperty val items: List<ArchiveMediaRequest>
)
