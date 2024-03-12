/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import org.signal.core.util.Base64

/**
 * Deserializes any valid base64 (regardless of padding or url-safety) into a ByteArray.
 */
class ByteArrayDeserializerBase64 : JsonDeserializer<ByteArray>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByteArray {
    return Base64.decode(p.valueAsString)
  }
}
