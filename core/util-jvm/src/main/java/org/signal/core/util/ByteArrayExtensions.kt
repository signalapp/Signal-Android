/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteArray.toUInt(): UInt {
  return ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).int.toUInt()
}

fun ByteArray.toUShort(): UShort {
  return ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).getShort().toUShort()
}
