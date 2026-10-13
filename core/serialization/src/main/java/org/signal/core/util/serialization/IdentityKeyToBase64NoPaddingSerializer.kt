/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.signal.core.util.Base64
import org.signal.libsignal.protocol.IdentityKey

class IdentityKeyToBase64NoPaddingSerializer : KSerializer<IdentityKey> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IdentityKey", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): IdentityKey {
    return IdentityKey(Base64.decode(decoder.decodeString()), 0)
  }

  override fun serialize(encoder: Encoder, value: IdentityKey) {
    encoder.encodeString(Base64.encodeWithoutPadding(value.serialize()))
  }
}
