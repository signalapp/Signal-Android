/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Kotlinx Serialization serializer for [RecipientId] objects.
 */
class RecipientIdSerializer : KSerializer<RecipientId> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RecipientId", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: RecipientId) {
    encoder.encodeString(value.serialize())
  }

  override fun deserialize(decoder: Decoder): RecipientId {
    return RecipientId.from(decoder.decodeString())
  }
}
