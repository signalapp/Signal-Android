/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.link

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response object for: GET /v1/devices/provisioning/code
 */
data class LinkedDeviceVerificationCodeResponse(
  @JsonProperty val verificationCode: String,
  @JsonProperty val tokenIdentifier: String
)
