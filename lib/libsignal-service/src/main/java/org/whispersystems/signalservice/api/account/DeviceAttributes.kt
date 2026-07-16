/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.signalservice.api.account

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Attributes sent when registering as a linked (secondary) device via `PUT /v1/devices/link`.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class DeviceAttributes @JsonCreator constructor(
  @JsonProperty val fetchesMessages: Boolean,
  @JsonProperty val registrationId: Int,
  @JsonProperty val pniRegistrationId: Int,
  @JsonProperty val name: String?,
  @JsonProperty val capabilities: AccountAttributes.Capabilities?
)
