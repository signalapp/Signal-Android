/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import org.signal.core.util.StreamUtil
import org.signal.core.util.logging.Log
import org.signal.libsignal.media.WebpSanitizer
import java.io.IOException
import java.io.InputStream

/**
 * Uses WebpSanitizer to check for invalid webp.
 */
class WebpSanDecoder : ResourceDecoder<InputStream, Bitmap> {

  companion object {
    private val TAG = Log.tag(WebpSanDecoder::class.java)

    private val MAGIC_NUMBER_P1 = byteArrayOf(0x52, 0x49, 0x46, 0x46) // "RIFF"
    private val MAGIC_NUMBER_P2 = byteArrayOf(0x57, 0x45, 0x42, 0x50) // "WEBP"

    private const val MAX_WEBP_COMPRESSED_SIZE = 10 * 1024 * 1024; // 10mb
  }

  /**
   * The "magic number" for a WEBP file is in the first 12 bytes. The layout is:
   *
   * [0-3]: "RIFF"
   * [4-7]: File length
   * [8-11]: "WEBP"
   *
   * We're not verifying the file length here, so we just need to check the first and last.
   *
   * We then sanitize the webp and block the load if the check fails.
   */
  override fun handles(source: InputStream, options: Options): Boolean {
    try {
      val magicNumberP1 = ByteArray(4)
      StreamUtil.readFully(source, magicNumberP1)

      val fileLength = ByteArray(4)
      StreamUtil.readFully(source, fileLength)

      val magicNumberP2 = ByteArray(4)
      StreamUtil.readFully(source, magicNumberP2)

      if (magicNumberP1.contentEquals(MAGIC_NUMBER_P1) && magicNumberP2.contentEquals(MAGIC_NUMBER_P2)) {
        try {
          source.reset()
          source.mark(MAX_WEBP_COMPRESSED_SIZE)
          WebpSanitizer.sanitize(source)
          source.reset()
        } catch (e: Exception) {
          Log.w(TAG, "Sanitize check failed or mark position invalidated by reset", e)
          return true
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to read magic number from stream!", e)
      return true
    }

    return false
  }

  override fun decode(source: InputStream, width: Int, height: Int, options: Options): Resource<Bitmap>? {
    Log.w(TAG, "Image did not pass sanitizer")
    throw IOException("Unable to load image")
  }
}
