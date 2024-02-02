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
import org.signal.glide.apng.ApngOptions
import org.signal.glide.common.io.InputStreamFactory
import java.io.ByteArrayInputStream
import java.io.IOException

class ApngInputStreamFactoryResourceDecoder : ResourceDecoder<InputStreamFactory, ApngDecoder> {

  override fun handles(source: InputStreamFactory, options: Options): Boolean {
    return if (options.get(ApngOptions.ANIMATE) == true) {
      ApngDecoder.isApng(source.create())
    } else {
      false
    }
  }

  @Throws(IOException::class)
  override fun decode(source: InputStreamFactory, width: Int, height: Int, options: Options): Resource<ApngDecoder>? {
    val data: ByteArray = source.create().readFully()
    val decoder = ApngDecoder(ByteArrayInputStream(data))
    return ApngResource(decoder, data.size)
  }
}
