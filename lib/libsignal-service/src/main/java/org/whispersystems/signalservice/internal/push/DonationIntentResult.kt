/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty

data class DonationIntentResult(
  val id: String,
  @JsonProperty("client_secret") val clientSecret: String
)
