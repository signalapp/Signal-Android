/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.Options
import org.signal.core.util.logging.Log
import org.signal.glide.common.io.InputStreamFactory

/**
 * See [WebpSanResourceDecoder]
 */
class WebpSanStreamFactoryDecoder<DecodeType : Any> : WebpSanResourceDecoder<InputStreamFactory, DecodeType>() {

  override fun handles(source: InputStreamFactory, options: Options): Boolean {
    return try {
      val isWebp = source.create().buffered().use { WebpSanitizerCheck.isWebp(it) }

      isWebp && source.create().buffered().use { !WebpSanitizerCheck.isSanitized(it) }
    } catch (e: Exception) {
      Log.w(tag, "Failed to check stream, blocking load.", e)
      true
    }
  }
}
