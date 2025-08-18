/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import org.signal.core.util.StreamUtil
import org.signal.glide.apng.decode.APNGDecoder
import org.signal.glide.apng.decode.APNGParser
import org.signal.glide.common.io.StreamReader
import org.thoughtcrime.securesms.glide.GlideStreamConfig
import org.thoughtcrime.securesms.mms.InputStreamFactory
import java.nio.ByteBuffer

/**
 * A variant of [StreamApngDecoder] that decodes animated PNGs from [InputStreamFactory] sources.
 */
class StreamFactoryApngDecoder(
  private val byteBufferDecoder: ResourceDecoder<ByteBuffer, APNGDecoder>
) : ResourceDecoder<InputStreamFactory, APNGDecoder> {

  override fun handles(source: InputStreamFactory, options: Options): Boolean {
    return if (options.get(ApngOptions.ANIMATE) == true) {
      APNGParser.isAPNG(LimitedReader(StreamReader(source.create()), GlideStreamConfig.markReadLimitBytes))
    } else {
      false
    }
  }

  override fun decode(
    source: InputStreamFactory,
    width: Int,
    height: Int,
    options: Options
  ): Resource<APNGDecoder>? {
    val data = StreamUtil.readFully(source.create())
    val byteBuffer = ByteBuffer.wrap(data)
    return byteBufferDecoder.decode(byteBuffer, width, height, options)
  }
}
