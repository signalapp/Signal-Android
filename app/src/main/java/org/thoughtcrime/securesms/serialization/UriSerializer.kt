/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.serialization

import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Kotlinx Serialization serializer for Android [Uri] objects.
 */
class UriSerializer : KSerializer<Uri> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Uri) {
    encoder.encodeString(value.toString())
  }

  override fun deserialize(decoder: Decoder): Uri {
    return Uri.parse(decoder.decodeString())
  }
}
