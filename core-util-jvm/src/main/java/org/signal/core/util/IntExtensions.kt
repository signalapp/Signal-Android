/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.nio.ByteBuffer

/**
 * Converts the integer into [ByteArray].
 */
fun Int.toByteArray(): ByteArray {
  return ByteBuffer
    .allocate(Int.SIZE_BYTES)
    .putInt(this)
    .array()
}
