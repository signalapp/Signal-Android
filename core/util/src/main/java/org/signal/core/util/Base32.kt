/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

/**
 * RFC 4648 base32. Padding is omitted on encode but tolerated on decode.
 */
object Base32 {

  private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
  private const val PADDING = '='

  private val DECODE_TABLE: IntArray = IntArray(128) { -1 }.apply {
    ALPHABET.forEachIndexed { index, char ->
      this[char.code] = index
      this[char.lowercaseChar().code] = index
    }
  }

  /** Encodes [data] as unpadded, uppercase base32. */
  fun encode(data: ByteArray): String {
    if (data.isEmpty()) {
      return ""
    }

    val out = StringBuilder((data.size * 8 + 4) / 5)
    var buffer = 0L
    var bitsBuffered = 0

    for (byte in data) {
      buffer = (buffer shl 8) or (byte.toLong() and 0xFF)
      bitsBuffered += 8

      while (bitsBuffered >= 5) {
        bitsBuffered -= 5
        out.append(ALPHABET[((buffer shr bitsBuffered) and 0x1F).toInt()])
      }
    }

    if (bitsBuffered > 0) {
      out.append(ALPHABET[((buffer shl (5 - bitsBuffered)) and 0x1F).toInt()])
    }

    return out.toString()
  }

  /**
   * Decodes base32 [input], ignoring padding and whitespace, or null if [input] contains anything else that isn't in
   * the base32 alphabet.
   */
  fun decodeOrNull(input: String): ByteArray? {
    val out = ArrayList<Byte>(input.length * 5 / 8 + 1)
    var buffer = 0L
    var bitsBuffered = 0

    for (char in input) {
      if (char == PADDING || char.isWhitespace()) {
        continue
      }

      val value = if (char.code < DECODE_TABLE.size) DECODE_TABLE[char.code] else -1
      if (value < 0) {
        return null
      }

      buffer = (buffer shl 5) or value.toLong()
      bitsBuffered += 5

      if (bitsBuffered >= 8) {
        bitsBuffered -= 8
        out += ((buffer shr bitsBuffered) and 0xFF).toByte()
      }
    }

    return out.toByteArray()
  }
}
