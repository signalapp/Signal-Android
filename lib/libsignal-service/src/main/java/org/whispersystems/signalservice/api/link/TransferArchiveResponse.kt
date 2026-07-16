/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.link

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response from the `transfer_archive` long-poll. The primary either provides where to find the link+sync
 * backup file ([cdn] + [key]), or reports an [error] indicating it will not provide one.
 */
data class TransferArchiveResponse @JsonCreator constructor(
  @JsonProperty("cdn") val cdn: Int? = null,
  @JsonProperty("key") val key: String? = null,
  @JsonProperty("error") val error: String? = null
) {
  companion object {
    /** The primary is requesting that this device re-link; no backup will be provided. */
    const val ERROR_RELINK_REQUESTED = "RELINK_REQUESTED"

    /** The primary has decided to abort the sync; continue linking without a backup. */
    const val ERROR_CONTINUE_WITHOUT_UPLOAD = "CONTINUE_WITHOUT_UPLOAD"
  }

  /** True if the primary provided a backup location (rather than an error). */
  val hasArchive: Boolean
    get() = cdn != null && key != null
}
