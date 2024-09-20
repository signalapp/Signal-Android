/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import com.google.protobuf.InvalidProtocolBufferException
import com.squareup.wire.ProtoAdapter

/**
 * Performs the common pattern of attempting to decode a serialized proto and returning null if it fails to decode.
 */
fun <E> ProtoAdapter<E>.decodeOrNull(serialized: ByteArray): E? {
  return try {
    this.decode(serialized)
  } catch (e: InvalidProtocolBufferException) {
    null
  }
}
