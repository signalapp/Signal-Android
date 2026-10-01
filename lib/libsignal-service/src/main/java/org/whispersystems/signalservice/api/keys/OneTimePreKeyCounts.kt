/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.keys

import com.fasterxml.jackson.annotation.JsonProperty

data class OneTimePreKeyCounts(
  @JsonProperty("count") val ecCount: Int = 0,
  @JsonProperty("pqCount") val kyberCount: Int = 0
)
