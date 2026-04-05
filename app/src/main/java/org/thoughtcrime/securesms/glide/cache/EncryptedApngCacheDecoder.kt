/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import org.signal.apng.ApngDecoder
import org.signal.core.util.logging.Log
import org.signal.core.util.stream.LimitedInputStream
import org.signal.glide.apng.ApngOptions
import java.io.File
import java.io.IOException

internal class EncryptedApngCacheDecoder(private val secret: ByteArray) : EncryptedCoder(), ResourceDecoder<File, ApngDecoder> {

  companion object {
    private val TAG = Log.tag(EncryptedApngCacheDecoder::class.java)
    private const val READ_LIMIT: Long = 5 * 1024 * 1024
  }

  override fun handles(source: File, options: Options): Boolean {
    if (options.get(ApngOptions.ANIMATE) != true) {
      return false
    }

    return try {
      createEncryptedInputStream(secret, source).use { inputStream ->
        ApngDecoder.isApng(LimitedInputStream(inputStream, READ_LIMIT))
      }
    } catch (e: IOException) {
      Log.w(TAG, e)
      false
    }
  }

  @Throws(IOException::class)
  override fun decode(source: File, width: Int, height: Int, options: Options): Resource<ApngDecoder>? {
    val decoder = ApngDecoder.create { createEncryptedInputStream(secret, source) }
    return ApngResource(decoder)
  }
}
