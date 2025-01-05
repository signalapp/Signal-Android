/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo

/**
 * Response body for GetCallingRelays
 */
data class GetCallingRelaysResponse @JsonCreator constructor(
  @JsonProperty("relays") val relays: List<TurnServerInfo>?
)
