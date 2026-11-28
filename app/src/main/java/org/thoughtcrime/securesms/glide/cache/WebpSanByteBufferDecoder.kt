/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.Options
import com.bumptech.glide.util.ByteBufferUtil
import org.signal.core.util.logging.Log
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * See [WebpSanResourceDecoder]
 */
class WebpSanByteBufferDecoder<DecodeType : Any> : WebpSanResourceDecoder<ByteBuffer, DecodeType>() {

  override fun handles(source: ByteBuffer, options: Options): Boolean {
    return try {
      val isWebp = source.stream().use { WebpSanitizerCheck.isWebp(it) }

      isWebp && source.stream().use { !WebpSanitizerCheck.isSanitized(it) }
    } catch (e: Exception) {
      Log.w(tag, "Failed to check buffer, blocking load.", e)
      true
    }
  }

  private fun ByteBuffer.stream(): InputStream = ByteBufferUtil.toStream(duplicate())
}
