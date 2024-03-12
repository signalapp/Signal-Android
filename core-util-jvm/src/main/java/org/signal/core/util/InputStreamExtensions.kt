/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.io.IOException
import java.io.InputStream

/**
 * Reads a 32-bit variable-length integer from the stream.
 *
 * The format uses one byte for each 7 bits of the integer, with the most significant bit (MSB) of each byte indicating whether more bytes need to be read.
 * If the MSB is 0, it indicates the final byte. The actual integer value is constructed from the remaining 7 bits of each byte.
 */
fun InputStream.readVarInt32(): Int {
  var result = 0

  // We read 7 bits of the integer at a time, up to the full size of an integer (32 bits).
  for (shift in 0 until 32 step 7) {
    // Despite returning an int, the range of the returned value is 0..255, so it's just a byte.
    // I believe it's an int just so it can return -1 when the stream ends.
    val byte: Int = read()
    if (byte < 0) {
      return -1
    }

    val lowestSevenBits = byte and 0x7F
    val shiftedBits = lowestSevenBits shl shift

    result = result or shiftedBits

    // If the MSB is 0, that means the varint is finished, and we have our full result
    if (byte and 0x80 == 0) {
      return result
    }
  }

  throw IOException("Malformed varint!")
}

/**
 * Reads the entire stream into a [ByteArray].
 */
@Throws(IOException::class)
fun InputStream.readFully(autoClose: Boolean = true): ByteArray {
  return StreamUtil.readFully(this, Integer.MAX_VALUE, autoClose)
}

/**
 * Fills reads data from the stream into the [buffer] until it is full.
 * Throws an [IOException] if the stream doesn't have enough data to fill the buffer.
 */
@Throws(IOException::class)
fun InputStream.readFully(buffer: ByteArray) {
  return StreamUtil.readFully(this, buffer)
}

/**
 * Reads the specified number of bytes from the stream and returns it as a [ByteArray].
 * Throws an [IOException] if the stream doesn't have that many bytes.
 */
@Throws(IOException::class)
fun InputStream.readNBytesOrThrow(length: Int): ByteArray {
  val buffer = ByteArray(length)
  this.readFully(buffer)
  return buffer
}

@Throws(IOException::class)
fun InputStream.readLength(): Long {
  val buffer = ByteArray(4096)
  var count = 0L

  while (this.read(buffer).also { if (it > 0) count += it } != -1) {
    // do nothing, all work is in the while condition
  }

  return count
}

/**
 * Reads the contents of the stream and discards them.
 */
@Throws(IOException::class)
fun InputStream.drain() {
  this.readLength()
}
