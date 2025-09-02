/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import org.signal.glide.common.io.InputStreamFactory
import org.signal.glide.load.resource.bitmap.Downsampler

/**
 * A Glide [ResourceDecoder] that decodes [Bitmap]s from a [InputStreamFactory] instances.
 */
class InputStreamFactoryBitmapDecoder(
  private val downsampler: Downsampler
) : ResourceDecoder<InputStreamFactory, Bitmap> {

  constructor(
    context: Context,
    glide: Glide,
    registry: Registry
  ) : this(
    downsampler = Downsampler(registry.imageHeaderParsers, context.resources.displayMetrics, glide.bitmapPool, glide.arrayPool)
  )

  override fun handles(source: InputStreamFactory, options: Options): Boolean = true

  override fun decode(source: InputStreamFactory, width: Int, height: Int, options: Options): Resource<Bitmap?>? {
    return downsampler.decode(source, width, height, options)
  }
}
