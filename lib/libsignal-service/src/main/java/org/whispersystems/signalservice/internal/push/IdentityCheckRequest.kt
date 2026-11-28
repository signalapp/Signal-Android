/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.signal.core.models.ServiceId
import org.signal.core.util.Base64
import org.signal.libsignal.protocol.IdentityKey
import org.signal.network.util.JsonUtil
import java.security.MessageDigest

class IdentityCheckRequest(
  @JsonProperty("elements") val serviceIdFingerprintPairs: List<ServiceIdFingerprintPair>
) {

  class ServiceIdFingerprintPair(
    @JsonProperty("uuid") @JsonSerialize(using = JsonUtil.ServiceIdSerializer::class) val serviceId: ServiceId,
    val fingerprint: String
  ) {
    constructor(serviceId: ServiceId, identityKey: IdentityKey) : this(
      serviceId,
      Base64.encodeWithPadding(MessageDigest.getInstance("SHA-256").digest(identityKey.serialize()), 0, 4)
    )
  }
}
