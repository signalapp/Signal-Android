/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.engine.Resource
import org.signal.core.util.logging.Log

/**
 * Base class for webp sanitization. It's a funny decoder with an inverted contract:
 * - if it's a bad webp, return true from [handles]
 * - if we handle it, this decoder just insta-fails to prevent anyone from displaying it.
 */
abstract class WebpSanResourceDecoder<Data : Any, DecodeType : Any> : ResourceDecoder<Data, DecodeType> {

  protected val tag: String = Log.tag(this::class)

  final override fun decode(source: Data, width: Int, height: Int, options: Options): Resource<DecodeType>? {
    Log.w(tag, "Image did not pass sanitizer")
    throw GlideException("Unable to load image")
  }
}
