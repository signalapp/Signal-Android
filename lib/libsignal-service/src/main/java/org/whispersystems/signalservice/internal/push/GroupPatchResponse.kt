/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/** Error response when attempting to patch group state. */
data class GroupPatchResponse @JsonCreator constructor(
  @JsonProperty val code: Int?,
  @JsonProperty val message: String?
)
