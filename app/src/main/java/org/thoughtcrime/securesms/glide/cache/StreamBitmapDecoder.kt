/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.load.ImageHeaderParser
import com.bumptech.glide.load.ImageHeaderParserUtils
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import org.signal.core.util.logging.Log
import java.io.IOException
import java.io.InputStream

typealias GlideStreamBitmapDecoder = com.bumptech.glide.load.resource.bitmap.StreamBitmapDecoder
typealias GlideDownsampler = com.bumptech.glide.load.resource.bitmap.Downsampler

class StreamBitmapDecoder(
  context: Context,
  glide: Glide,
  registry: Registry
) : ResourceDecoder<InputStream, Bitmap> {

  private val imageHeaderParsers = registry.imageHeaderParsers
  private val arrayPool = glide.arrayPool
  private val downsampler = GlideDownsampler(imageHeaderParsers, context.resources.displayMetrics, glide.bitmapPool, arrayPool)
  private val delegate = GlideStreamBitmapDecoder(downsampler, arrayPool)

  override fun handles(source: InputStream, options: Options): Boolean {
    if (!delegate.handles(source, options)) {
      return false
    }

    val imageType = try {
      ImageHeaderParserUtils.getType(imageHeaderParsers, source, arrayPool)
    } catch (e: IOException) {
      Log.w(TAG, "Error checking image type.", e)
      ImageHeaderParser.ImageType.UNKNOWN
    }

    return when (imageType) {
      ImageHeaderParser.ImageType.GIF,
      ImageHeaderParser.ImageType.PNG_A,
      ImageHeaderParser.ImageType.WEBP_A,
      ImageHeaderParser.ImageType.ANIMATED_WEBP -> false

      else -> true
    }
  }

  override fun decode(source: InputStream, width: Int, height: Int, options: Options): Resource<Bitmap?>? {
    return delegate.decode(source, width, height, options)
  }

  companion object {
    private val TAG = Log.tag(StreamBitmapDecoder::class)
  }
}
