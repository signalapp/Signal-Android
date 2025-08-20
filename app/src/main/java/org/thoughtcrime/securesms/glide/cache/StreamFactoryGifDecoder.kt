/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.load.resource.gif.StreamGifDecoder
import org.thoughtcrime.securesms.mms.InputStreamFactory

/**
 * A variant of [StreamGifDecoder] that decodes animated PNGs from [InputStreamFactory] sources.
 */
class StreamFactoryGifDecoder(
  private val streamGifDecoder: StreamGifDecoder
) : ResourceDecoder<InputStreamFactory, GifDrawable> {

  override fun handles(source: InputStreamFactory, options: Options): Boolean = true

  override fun decode(
    source: InputStreamFactory,
    width: Int,
    height: Int,
    options: Options
  ): Resource<GifDrawable>? {
    return streamGifDecoder.decode(source.create(), width, height, options)
  }
}
