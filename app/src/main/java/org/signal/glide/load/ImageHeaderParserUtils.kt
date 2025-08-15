/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.glide.load

import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.load.ImageHeaderParser
import com.bumptech.glide.load.data.ParcelFileDescriptorRewinder
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool
import com.bumptech.glide.load.resource.bitmap.RecyclableBufferedInputStream
import org.signal.glide.common.io.GlideStreamConfig
import org.signal.glide.common.io.InputStreamFactory
import org.thoughtcrime.securesms.util.BitmapUtil
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

typealias GlideImageHeaderParserUtils = com.bumptech.glide.load.ImageHeaderParserUtils

object ImageHeaderParserUtils {
  /**
   * @see com.bumptech.glide.load.ImageHeaderParserUtils.getType
   */
  @JvmStatic
  @Throws(IOException::class)
  fun getType(
    parsers: List<ImageHeaderParser>,
    inputStream: InputStream?,
    byteArrayPool: ArrayPool
  ): ImageHeaderParser.ImageType {
    if (inputStream == null) {
      return ImageHeaderParser.ImageType.UNKNOWN
    }

    val markableStream = if (!inputStream.markSupported()) {
      RecyclableBufferedInputStream(inputStream, byteArrayPool)
    } else {
      inputStream
    }

    markableStream.mark(GlideStreamConfig.markReadLimitBytes)

    return getType(
      parsers = parsers,
      getTypeAndRewind = { parser ->
        try {
          parser.getType(markableStream)
        } finally {
          markableStream.reset()
        }
      }
    )
  }

  /**
   * @see com.bumptech.glide.load.ImageHeaderParserUtils.getType
   */
  @JvmStatic
  @Throws(IOException::class)
  fun getType(
    parsers: List<ImageHeaderParser>,
    buffer: ByteBuffer
  ): ImageHeaderParser.ImageType {
    return GlideImageHeaderParserUtils.getType(parsers, buffer)
  }

  /**
   * @see com.bumptech.glide.load.ImageHeaderParserUtils.getType
   */
  @JvmStatic
  @Throws(IOException::class)
  fun getType(
    parsers: List<ImageHeaderParser>,
    parcelFileDescriptorRewinder: ParcelFileDescriptorRewinder,
    byteArrayPool: ArrayPool
  ): ImageHeaderParser.ImageType {
    return GlideImageHeaderParserUtils.getType(parsers, parcelFileDescriptorRewinder, byteArrayPool)
  }

  private fun getType(
    parsers: List<ImageHeaderParser>,
    getTypeAndRewind: (ImageHeaderParser) -> ImageHeaderParser.ImageType
  ): ImageHeaderParser.ImageType {
    return parsers.firstNotNullOfOrNull { parser ->
      getTypeAndRewind(parser)
        .takeIf { type -> type != ImageHeaderParser.ImageType.UNKNOWN }
    } ?: ImageHeaderParser.ImageType.UNKNOWN
  }

  /**
   * @see com.bumptech.glide.load.ImageHeaderParserUtils.getOrientation
   */
  @JvmStatic
  @Throws(IOException::class)
  fun getOrientationWithFallbacks(
    parsers: List<ImageHeaderParser>,
    buffer: ByteBuffer,
    arrayPool: ArrayPool
  ): Int {
    return GlideImageHeaderParserUtils.getOrientation(parsers, buffer, arrayPool)
  }

  /**
   * @see com.bumptech.glide.load.ImageHeaderParserUtils.getOrientation
   */
  @JvmStatic
  @Throws(IOException::class)
  fun getOrientation(
    parsers: List<ImageHeaderParser>,
    parcelFileDescriptorRewinder: ParcelFileDescriptorRewinder,
    byteArrayPool: ArrayPool
  ): Int {
    return GlideImageHeaderParserUtils.getOrientation(parsers, parcelFileDescriptorRewinder, byteArrayPool)
  }

  /**
   * @see com.bumptech.glide.load.ImageHeaderParserUtils.getOrientation
   */
  @JvmStatic
  @Throws(IOException::class)
  fun getOrientation(
    parsers: List<ImageHeaderParser>,
    inputStream: InputStream?,
    byteArrayPool: ArrayPool,
    allowStreamRewind: Boolean
  ): Int {
    if (inputStream == null) {
      return ImageHeaderParser.UNKNOWN_ORIENTATION
    }

    val markableStream = if (allowStreamRewind && !inputStream.markSupported()) {
      RecyclableBufferedInputStream(inputStream, byteArrayPool)
    } else {
      inputStream
    }

    if (allowStreamRewind) {
      markableStream.mark(GlideStreamConfig.markReadLimitBytes)
    }

    return getOrientation(
      parsers = parsers,
      getOrientationAndRewind = { parser ->
        try {
          parser.getOrientation(markableStream, byteArrayPool)
        } finally {
          if (allowStreamRewind) {
            markableStream.reset()
          }
        }
      }
    )
  }

  /**
   * @see com.bumptech.glide.load.ImageHeaderParserUtils.getOrientation
   */
  @JvmStatic
  @Throws(IOException::class)
  fun getOrientationWithFallbacks(
    parsers: List<ImageHeaderParser>,
    inputStreamFactory: InputStreamFactory,
    byteArrayPool: ArrayPool
  ): Int {
    val orientationFromParsers = getOrientation(
      parsers = parsers,
      inputStream = inputStreamFactory.createRecyclable(byteArrayPool),
      byteArrayPool = byteArrayPool,
      allowStreamRewind = false
    )
    if (orientationFromParsers != ImageHeaderParser.UNKNOWN_ORIENTATION) return orientationFromParsers

    val orientationFromExif = BitmapUtil.getExifOrientation(ExifInterface(inputStreamFactory.createRecyclable(byteArrayPool)))
    if (orientationFromExif != ImageHeaderParser.UNKNOWN_ORIENTATION) return orientationFromExif

    return ImageHeaderParser.UNKNOWN_ORIENTATION
  }

  private fun getOrientation(
    parsers: List<ImageHeaderParser>,
    getOrientationAndRewind: (ImageHeaderParser) -> Int
  ): Int {
    return parsers.firstNotNullOfOrNull { parser ->
      getOrientationAndRewind(parser)
        .takeIf { type -> type != ImageHeaderParser.UNKNOWN_ORIENTATION }
    } ?: ImageHeaderParser.UNKNOWN_ORIENTATION
  }
}
