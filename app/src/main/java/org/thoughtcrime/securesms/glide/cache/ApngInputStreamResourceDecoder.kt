/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import org.signal.apng.ApngDecoder
import org.signal.core.util.readFully
import org.signal.core.util.stream.LimitedInputStream
import org.signal.glide.apng.ApngOptions
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class ApngInputStreamResourceDecoder : ResourceDecoder<InputStream, ApngDecoder> {
  companion object {
    /** Set to match [com.bumptech.glide.load.data.InputStreamRewinder]'s read limit  */
    private const val READ_LIMIT: Long = 5 * 1024 * 1024
  }

  override fun handles(source: InputStream, options: Options): Boolean {
    return if (options.get(ApngOptions.ANIMATE)!!) {
      ApngDecoder.isApng(LimitedInputStream(source, READ_LIMIT))
    } else {
      false
    }
  }

  @Throws(IOException::class)
  override fun decode(source: InputStream, width: Int, height: Int, options: Options): Resource<ApngDecoder>? {
    val data: ByteArray = source.readFully()
    val decoder = ApngDecoder(ByteArrayInputStream(data))
    return ApngResource(decoder, data.size)
  }
}
