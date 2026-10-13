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
import org.signal.core.models.ServiceId.ACI

class ACISerializer : KSerializer<ACI> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ACI", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): ACI {
    return ACI.parseOrThrow(decoder.decodeString())
  }

  override fun serialize(encoder: Encoder, value: ACI) {
    encoder.encodeString(value.toString())
  }
}
