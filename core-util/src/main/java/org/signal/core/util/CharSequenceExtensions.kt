/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

/**
 * A copy of [okio.utf8Size] that works on [CharSequence].
 */
fun CharSequence.utf8Size(): Int {
  var result = 0
  var i = 0
  while (i < this.length) {
    val c = this[i].code

    if (c < 0x80) {
      // A 7-bit character with 1 byte.
      result++
      i++
    } else if (c < 0x800) {
      // An 11-bit character with 2 bytes.
      result += 2
      i++
    } else if (c < 0xd800 || c > 0xdfff) {
      // A 16-bit character with 3 bytes.
      result += 3
      i++
    } else {
      val low = if (i + 1 < this.length) this[i + 1].code else 0
      if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
        // A malformed surrogate, which yields '?'.
        result++
        i++
      } else {
        // A 21-bit character with 4 bytes.
        result += 4
        i += 2
      }
    }
  }

  return result
}
