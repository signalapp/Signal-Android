/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.link

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response body for GET /v1/devices/wait_for_linked_device/{tokenIdentifier}
 */
data class WaitForLinkedDeviceResponse(
  @JsonProperty val id: Int,
  @JsonProperty val name: String,
  @JsonProperty val lastSeen: Long,
  @JsonProperty val registrationId: Int,
  @JsonProperty val createdAtCiphertext: String?
)
