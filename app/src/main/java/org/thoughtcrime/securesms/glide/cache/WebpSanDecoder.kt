/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.Options
import org.signal.core.util.logging.Log
import java.io.InputStream

/**
 * See [WebpSanResourceDecoder]
 */
class WebpSanDecoder<DecodeType : Any> : WebpSanResourceDecoder<InputStream, DecodeType>() {

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
      Log.w(tag, "Failed to check stream, blocking load.", e)
      true
    }
  }
}
