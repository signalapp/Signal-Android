/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response to archiving media, backup CDN number where media is located.
 */
class ArchiveMediaResponse(
  @JsonProperty val cdn: Int
)
