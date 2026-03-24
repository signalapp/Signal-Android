/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.signal.core.util.Base64
import org.signal.libsignal.protocol.ecc.ECPublicKey

/**
 * Represents the request body when setting the archive public key.
 */
class ArchiveSetPublicKeyRequest(
  @JsonProperty
  @JsonSerialize(using = PublicKeySerializer::class)
  val backupIdPublicKey: ECPublicKey
) {
  class PublicKeySerializer : JsonSerializer<ECPublicKey>() {
    override fun serialize(value: ECPublicKey, gen: JsonGenerator, serializers: SerializerProvider) {
      gen.writeString(Base64.encodeWithPadding(value.serialize()))
    }
  }
}
