/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.signal.core.models.ServiceId
import org.signal.libsignal.protocol.IdentityKey
import org.signal.network.util.JsonUtil

class IdentityCheckResponse(
  @JsonProperty("elements") val serviceIdKeyPairs: List<ServiceIdentityPair>? = null
) {

  class ServiceIdentityPair(
    @JsonProperty("uuid") @JsonDeserialize(using = JsonUtil.ServiceIdDeserializer::class) val serviceId: ServiceId? = null,
    @JsonDeserialize(using = JsonUtil.IdentityKeyDeserializer::class) val identityKey: IdentityKey? = null
  )
}
