/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialResponse
import org.whispersystems.util.Base64

/**
 * Response body for CreateCallLinkAuthResponse
 */
data class CreateCallLinkAuthResponse @JsonCreator constructor(
  @JsonProperty("credential") val credential: String,
  @JsonProperty("redemptionTime") val redemptionTime: Long
) {
  val createCallLinkCredentialResponse: CreateCallLinkCredentialResponse
    get() = CreateCallLinkCredentialResponse(Base64.decode(credential))
}
