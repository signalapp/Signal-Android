/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.signal.core.util.Base64
import org.signal.libsignal.protocol.ecc.ECPublicKey

data class PreKeyEntity(
  val keyId: Long,
  @JsonSerialize(using = ECPublicKeySerializer::class)
  @JsonDeserialize(using = ECPublicKeyDeserializer::class)
  val publicKey: ECPublicKey
) {
  class ECPublicKeySerializer : JsonSerializer<ECPublicKey>() {
    override fun serialize(value: ECPublicKey, gen: JsonGenerator, serializers: SerializerProvider) {
      gen.writeString(Base64.encodeWithoutPadding(value.serialize()))
    }
  }

  class ECPublicKeyDeserializer : JsonDeserializer<ECPublicKey>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ECPublicKey {
      return ECPublicKey(Base64.decode(p.valueAsString))
    }
  }
}
