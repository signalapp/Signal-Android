/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import org.signal.core.util.StreamUtil
import org.signal.core.util.logging.Log
import org.signal.libsignal.media.WebpSanitizer
import java.io.InputStream

/**
 * Shared webp detection and sanitization logic used by the various WebpSan decoders.
 */
object WebpSanitizerCheck {

  private val TAG = Log.tag(WebpSanitizerCheck::class)

  private val MAGIC_NUMBER_P1 = byteArrayOf(0x52, 0x49, 0x46, 0x46) // "RIFF"
  private val MAGIC_NUMBER_P2 = byteArrayOf(0x57, 0x45, 0x42, 0x50) // "WEBP"

  const val MAX_WEBP_COMPRESSED_SIZE = 10 * 1024 * 1024 // 10mb

  /**
   * The "magic number" for a WEBP file is in the first 12 bytes. The layout is:
   *
   * [0-3]: "RIFF"
   * [4-7]: File length
   * [8-11]: "WEBP"
   *
   * We're not verifying the file length here, so we just need to check the first and last.
   *
   * Consumes the first 12 bytes of [source], which must be positioned at the start of the file.
   */
  fun isWebp(source: InputStream): Boolean {
    val magicNumberP1 = ByteArray(4)
    StreamUtil.readFully(source, magicNumberP1)

    val fileLength = ByteArray(4)
    StreamUtil.readFully(source, fileLength)

    val magicNumberP2 = ByteArray(4)
    StreamUtil.readFully(source, magicNumberP2)

    return magicNumberP1.contentEquals(MAGIC_NUMBER_P1) && magicNumberP2.contentEquals(MAGIC_NUMBER_P2)
  }

  /**
   * Runs [source] through libsignal's [WebpSanitizer], returning true if it passed. [source] must be positioned at the start of the file, and will be fully
   * consumed.
   */
  fun isSanitized(source: InputStream): Boolean {
    return try {
      WebpSanitizer.sanitize(source)
      true
    } catch (e: Exception) {
      Log.w(TAG, "Sanitize check failed", e)
      false
    }
  }
}
