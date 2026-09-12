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
import org.signal.libsignal.protocol.kem.KEMPublicKey

class KyberPreKeyEntity(
  val keyId: Long,
  @JsonSerialize(using = KEMPublicKeySerializer::class)
  @JsonDeserialize(using = KEMPublicKeyDeserializer::class)
  val publicKey: KEMPublicKey,
  @JsonSerialize(using = ByteArraySerializerBase64NoPadding::class)
  @JsonDeserialize(using = ByteArrayDeserializerBase64::class)
  val signature: ByteArray
) {
  class KEMPublicKeySerializer : JsonSerializer<KEMPublicKey>() {
    override fun serialize(value: KEMPublicKey, gen: JsonGenerator, serializers: SerializerProvider) {
      gen.writeString(Base64.encodeWithoutPadding(value.serialize()))
    }
  }

  class KEMPublicKeyDeserializer : JsonDeserializer<KEMPublicKey>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): KEMPublicKey {
      return KEMPublicKey(Base64.decode(p.valueAsString), 0)
    }
  }
}
