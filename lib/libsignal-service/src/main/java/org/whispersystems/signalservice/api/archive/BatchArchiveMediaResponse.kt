/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Multi-response data for a batch archive media operation.
 */
class BatchArchiveMediaResponse(
  @JsonProperty val responses: List<BatchArchiveMediaItemResponse>
) {
  class BatchArchiveMediaItemResponse(
    @JsonProperty val status: Int?,
    @JsonProperty val failureReason: String?,
    @JsonProperty val cdn: Int?,
    @JsonProperty val mediaId: String
  )
}
