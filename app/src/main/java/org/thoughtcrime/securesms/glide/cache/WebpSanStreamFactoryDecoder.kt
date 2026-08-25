/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.engine.Resource
import org.signal.core.util.logging.Log
import org.signal.glide.common.io.InputStreamFactory

/**
 * The [InputStreamFactory] equivalent of [WebpSanDecoder]. Because we can create as many streams as we like, we don't have to deal with marking and resetting
 * the caller's stream.
 */
class WebpSanStreamFactoryDecoder : ResourceDecoder<InputStreamFactory, Bitmap> {

  companion object {
    private val TAG = Log.tag(WebpSanStreamFactoryDecoder::class)
  }

  /**
   * If the source is a webp, we sanitize it and block the load if the check fails.
   */
  override fun handles(source: InputStreamFactory, options: Options): Boolean {
    return try {
      val isWebp = source.create().buffered().use { WebpSanitizerCheck.isWebp(it) }

      isWebp && source.create().buffered().use { !WebpSanitizerCheck.isSanitized(it) }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to check stream, blocking load.", e)
      true
    }
  }

  /**
   * Note that we throw a [GlideException] rather than an [java.io.IOException] on purpose. Glide swallows IOExceptions from a decoder and simply moves on to
   * the next decoder registered for the same data/resource pair, which would let the unsanitized image load anyway. A GlideException aborts the entire decode
   * path.
   */
  override fun decode(source: InputStreamFactory, width: Int, height: Int, options: Options): Resource<Bitmap>? {
    Log.w(TAG, "Image did not pass sanitizer")
    throw GlideException("Unable to load image")
  }
}
