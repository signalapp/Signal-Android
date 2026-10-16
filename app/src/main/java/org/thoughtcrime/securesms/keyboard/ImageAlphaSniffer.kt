/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyboard

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Answers "does this image carry an alpha channel?" from the file header alone. Pixel data is never
 * decoded, so the check reads a handful of bytes and cannot time out. Anything that is not a PNG or
 * a WebP, or that cannot be parsed, is reported as opaque.
 */
object ImageAlphaSniffer {

  private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

  private const val PNG_IHDR_LENGTH = 13

  /**
   * How far into a PNG we look for a tRNS chunk, and how large a single chunk in front of it may be.
   * tRNS normally sits right after IHDR or PLTE, well within the first kilobyte.
   */
  private const val PNG_MAX_SCAN_BYTES = 64 * 1024

  private const val WEBP_VP8X_ALPHA_FLAG = 0x10
  private const val WEBP_VP8L_SIGNATURE = 0x2F
  private const val WEBP_VP8L_ALPHA_FLAG = 0x10

  /** Closes [inputStream] when done. */
  fun hasAlpha(inputStream: InputStream): Boolean {
    return try {
      DataInputStream(BufferedInputStream(inputStream)).use { stream ->
        val head = ByteArray(12)
        stream.readFully(head)
        when {
          head.copyOf(8).contentEquals(PNG_SIGNATURE) -> pngHasAlpha(stream, firstChunkLength = readIntBigEndian(head, 8))
          head.ascii(0, 4) == "RIFF" && head.ascii(8, 4) == "WEBP" -> webpHasAlpha(stream)
          else -> false
        }
      }
    } catch (e: IOException) {
      false
    }
  }

  /**
   * After the 8-byte signature a PNG is a sequence of chunks: 4-byte big-endian length, 4-byte type,
   * data, 4-byte CRC. The first chunk is always IHDR, whose byte 9 is the color type.
   */
  private fun pngHasAlpha(stream: DataInputStream, firstChunkLength: Int): Boolean {
    val type = ByteArray(4)
    stream.readFully(type)
    if (type.ascii() != "IHDR" || firstChunkLength != PNG_IHDR_LENGTH) {
      return false
    }

    val ihdr = ByteArray(PNG_IHDR_LENGTH)
    stream.readFully(ihdr)
    stream.skipFully(4) // CRC

    return when (ihdr[9].toInt() and 0xFF) {
      4, 6 -> true // grayscale + alpha, RGB + alpha
      0, 2, 3 -> pngHasTransparencyChunk(stream) // grayscale, RGB, palette: transparent only if a tRNS chunk follows
      else -> false
    }
  }

  /** Walks the chunks between IHDR and the first IDAT looking for tRNS. */
  private fun pngHasTransparencyChunk(stream: DataInputStream): Boolean {
    val type = ByteArray(4)
    var offset = 8 + 4 + 4 + PNG_IHDR_LENGTH + 4
    while (offset < PNG_MAX_SCAN_BYTES) {
      val length = stream.readInt()
      stream.readFully(type)
      when (type.ascii()) {
        "tRNS" -> return true
        "IDAT", "IEND" -> return false
      }
      if (length < 0 || length > PNG_MAX_SCAN_BYTES) {
        return false
      }
      stream.skipFully(length.toLong() + 4) // data + CRC
      offset += 4 + 4 + length + 4
    }
    return false
  }

  /**
   * A WebP is a RIFF container: "RIFF", file size, "WEBP", then chunks of 4-byte FourCC, 4-byte
   * little-endian size and payload. Alpha is announced in the first chunk.
   */
  private fun webpHasAlpha(stream: DataInputStream): Boolean {
    val fourcc = ByteArray(4)
    stream.readFully(fourcc)
    stream.skipFully(4) // chunk size

    return when (fourcc.ascii()) {
      // Extended format: the first payload byte holds the feature flags, bit 4 is "Alpha".
      "VP8X" -> (stream.readUnsignedByte() and WEBP_VP8X_ALPHA_FLAG) != 0
      // Lossless format: one signature byte, then a 32-bit little-endian field whose bit 28 is "alpha_is_used".
      "VP8L" -> {
        if (stream.readUnsignedByte() != WEBP_VP8L_SIGNATURE) {
          false
        } else {
          stream.skipFully(3)
          (stream.readUnsignedByte() and WEBP_VP8L_ALPHA_FLAG) != 0
        }
      }
      // Simple lossy format ("VP8 ") cannot carry alpha.
      else -> false
    }
  }

  private fun ByteArray.ascii(offset: Int = 0, length: Int = size): String {
    return String(this, offset, length, Charsets.US_ASCII)
  }

  private fun readIntBigEndian(bytes: ByteArray, offset: Int): Int {
    return ((bytes[offset].toInt() and 0xFF) shl 24) or
      ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
      ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
      (bytes[offset + 3].toInt() and 0xFF)
  }

  private fun DataInputStream.skipFully(count: Long) {
    var remaining = count
    while (remaining > 0) {
      val skipped = skip(remaining)
      if (skipped <= 0) {
        if (read() < 0) {
          throw EOFException()
        }
        remaining -= 1
      } else {
        remaining -= skipped
      }
    }
  }
}
