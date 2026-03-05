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
) {
  enum class StatusCodes(val code: Int) {
    BadArguments(400),
    InvalidPresentationOrSignature(401),
    InsufficientPermissions(403),
    NoMediaSpaceRemaining(413),
    RateLimited(429),
    Unknown(-1);

    companion object {
      fun from(code: Int): StatusCodes {
        return entries.firstOrNull { it.code == code } ?: Unknown
      }
    }
  }
}
