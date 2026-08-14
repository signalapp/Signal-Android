/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a 32-bit variable-length integer to the stream.
 *
 * The format uses one byte for each 7 bits of the integer, with the most significant bit (MSB) of each byte indicating whether more bytes need to be read.
 */
fun OutputStream.writeVarInt32(value: Int) {
  var remaining = value

  while (true) {
    // We write 7 bits of the integer at a time
    val lowestSevenBits = remaining and 0x7F
    remaining = remaining ushr 7

    if (remaining == 0) {
      // If there are no more bits to write, we're done
      write(lowestSevenBits)
      return
    } else {
      // Otherwise, we need to write the next 7 bits, and set the MSB to 1 to indicate that there are more bits to come
      write(lowestSevenBits or 0x80)
    }
  }
}

/**
 * Writes a 32-bit unsigned integer to the stream.
 */
fun OutputStream.writeUInt(value: UInt) {
  // Note that casting to an int here is fine, because at the end of the day, we're just writing 4 bytes to the stream
  this.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.toInt()).array())
}

/**
 * Writes [count] zero bytes to the stream, using a bounded buffer so that we never allocate the full [count] at once.
 * A non-positive [count] writes nothing.
 */
fun OutputStream.writeZeros(count: Long, maxBufferSize: Int = 32 * 1024) {
  writeRepeated(0, count, maxBufferSize)
}

/**
 * Writes [value] to the stream [count] times, using a bounded buffer so that we never allocate the full [count] at once.
 * A non-positive [count] writes nothing.
 */
fun OutputStream.writeRepeated(value: Byte, count: Long, maxBufferSize: Int = 32 * 1024) {
  require(maxBufferSize > 0) { "maxBufferSize must be positive, was $maxBufferSize" }

  if (count <= 0) {
    return
  }

  val buffer = ByteArray(minOf(count, maxBufferSize.toLong()).toInt())
  if (value != 0.toByte()) {
    buffer.fill(value)
  }

  var remaining = count
  while (remaining > 0) {
    val chunkSize = minOf(remaining, buffer.size.toLong()).toInt()
    this.write(buffer, 0, chunkSize)
    remaining -= chunkSize
  }
}
