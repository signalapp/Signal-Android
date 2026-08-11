/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import org.signal.core.util.logging.Log
import java.io.IOException
import java.io.InputStream

/**
 * Uses WebpSanitizer to check for invalid webp.
 *
 * See [WebpSanStreamFactoryDecoder] for the equivalent that operates on the [org.signal.glide.common.io.InputStreamFactory] model chain.
 */
class WebpSanDecoder : ResourceDecoder<InputStream, Bitmap> {

  companion object {
    private val TAG = Log.tag(WebpSanDecoder::class.java)
  }

  /**
   * If the source is a webp, we sanitize it and block the load if the check fails.
   */
  override fun handles(source: InputStream, options: Options): Boolean {
    return try {
      if (!WebpSanitizerCheck.isWebp(source)) {
        return false
      }

      source.reset()
      source.mark(WebpSanitizerCheck.MAX_WEBP_COMPRESSED_SIZE)
      val sanitized = WebpSanitizerCheck.isSanitized(source)
      source.reset()

      !sanitized
    } catch (e: Exception) {
      Log.w(TAG, "Failed to check stream, blocking load.", e)
      true
    }
  }

  override fun decode(source: InputStream, width: Int, height: Int, options: Options): Resource<Bitmap>? {
    Log.w(TAG, "Image did not pass sanitizer")
    throw IOException("Unable to load image")
  }
}
