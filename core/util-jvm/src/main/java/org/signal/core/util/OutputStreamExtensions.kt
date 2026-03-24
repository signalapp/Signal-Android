/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.io.OutputStream

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
