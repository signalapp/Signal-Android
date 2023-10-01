/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.glide.webp

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import org.signal.core.util.StreamUtil
import org.signal.core.util.logging.Log
import java.io.IOException
import java.io.InputStream

class WebpInputStreamResourceDecoder(private val bitmapPool: BitmapPool) : ResourceDecoder<InputStream, Bitmap> {

  companion object {
    private const val TAG = "WebpResourceDecoder" // Name > 23 characters

    private val MAGIC_NUMBER_P1 = byteArrayOf(0x52, 0x49, 0x46, 0x46) // "RIFF"
    private val MAGIC_NUMBER_P2 = byteArrayOf(0x57, 0x45, 0x42, 0x50) // "WEBP"

    private const val MAX_WEBP_COMPRESSED_SIZE = 10 * 1024 * 1024; // 10mb
  }

  /**
   * The "magic number" for a WEBP file is in the first 12 bytes. The layout is:
   *
   * [0-3]: "RIFF"
   * [4-7]: File length
   * [8-11]: "WEBP"
   *
   * We're not verifying the file length here, so we just need to check the first and last
   */
  override fun handles(source: InputStream, options: Options): Boolean {
    return try {
      val magicNumberP1 = ByteArray(4)
      StreamUtil.readFully(source, magicNumberP1)

      val fileLength = ByteArray(4)
      StreamUtil.readFully(source, fileLength)

      val magicNumberP2 = ByteArray(4)
      StreamUtil.readFully(source, magicNumberP2)

      magicNumberP1.contentEquals(MAGIC_NUMBER_P1) && magicNumberP2.contentEquals(MAGIC_NUMBER_P2)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to read magic number from stream!", e)
      false
    }
  }

  override fun decode(source: InputStream, width: Int, height: Int, options: Options): Resource<Bitmap>? {
    Log.d(TAG, "decode()")

    val webp: ByteArray = try {
      StreamUtil.readFully(source, MAX_WEBP_COMPRESSED_SIZE)
    } catch (e: IOException) {
      Log.w(TAG, "Unexpected IOException hit while reading image data", e)
      throw e
    }

    val bitmap: Bitmap? = WebpDecoder().nativeDecodeBitmapScaled(webp, width, height)
    return BitmapResource.obtain(bitmap, bitmapPool)
  }
}
