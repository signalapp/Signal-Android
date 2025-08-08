/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide

import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.load.ImageHeaderParser
import com.bumptech.glide.load.data.ParcelFileDescriptorRewinder
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool
import org.thoughtcrime.securesms.mms.InputStreamFactory
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
    inputStream: InputStream,
    byteArrayPool: ArrayPool
  ): ImageHeaderParser.ImageType {
    return GlideImageHeaderParserUtils.getType(parsers, inputStream, byteArrayPool)
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

  @JvmStatic
  @Throws(IOException::class)
  fun getOrientation(
    parsers: List<ImageHeaderParser>,
    inputStream: InputStream,
    byteArrayPool: ArrayPool
  ): Int {
    return GlideImageHeaderParserUtils.getOrientation(parsers, inputStream, byteArrayPool)
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
    val orientationFromParsers = getOrientationFromParsers(
      parsers = parsers,
      inputStream = inputStreamFactory.createRecyclable(byteArrayPool),
      byteArrayPool = byteArrayPool
    )
    if (orientationFromParsers != ImageHeaderParser.UNKNOWN_ORIENTATION) return orientationFromParsers

    val orientationFromExif = getOrientationFromExif(inputStream = inputStreamFactory.createRecyclable(byteArrayPool))
    if (orientationFromExif != ImageHeaderParser.UNKNOWN_ORIENTATION) return orientationFromExif

    return ImageHeaderParser.UNKNOWN_ORIENTATION
  }

  private fun getOrientationFromParsers(
    parsers: List<ImageHeaderParser>,
    inputStream: InputStream?,
    byteArrayPool: ArrayPool
  ): Int {
    if (inputStream == null) {
      return ImageHeaderParser.UNKNOWN_ORIENTATION
    }

    return getOrientation(
      parsers = parsers,
      readOrientation = { parser -> parser.getOrientation(inputStream, byteArrayPool) }
    )
  }

  private fun getOrientationFromExif(inputStream: InputStream): Int {
    return BitmapUtil.getExifOrientation(ExifInterface(inputStream))
  }

  private fun getOrientation(
    parsers: List<ImageHeaderParser>,
    readOrientation: (ImageHeaderParser) -> Int
  ): Int {
    parsers.forEach { parser ->
      val orientation = readOrientation(parser)
      if (orientation != ImageHeaderParser.UNKNOWN_ORIENTATION) {
        return orientation
      }
    }

    return ImageHeaderParser.UNKNOWN_ORIENTATION
  }
}
