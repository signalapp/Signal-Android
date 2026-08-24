/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import org.signal.core.util.logging.Log
import org.signal.glide.common.io.InputStreamFactory
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * The [InputStreamFactory] equivalent of [EncryptedCacheDecoder], letting a decoder that wants to open the encrypted cache file multiple times do so.
 */
class EncryptedCacheStreamFactoryDecoder<DecodeType>(
  secret: ByteArray,
  private val decoder: ResourceDecoder<InputStreamFactory, DecodeType>
) : ResourceDecoder<File, DecodeType> {

  companion object {
    private val TAG = Log.tag(EncryptedCacheStreamFactoryDecoder::class)
  }

  private val coder = EncryptedStreamFactoryCoder(secret)

  override fun handles(source: File, options: Options): Boolean {
    val factory = coder.factoryFor(source)

    try {
      factory.create().close()
    } catch (e: IOException) {
      Log.w(TAG, "Not a readable encrypted cache file.", e)
      return false
    }

    return decoder.handles(factory, options)
  }

  override fun decode(source: File, width: Int, height: Int, options: Options): Resource<DecodeType>? {
    return decoder.decode(coder.factoryFor(source), width, height, options)
  }

  private class EncryptedStreamFactoryCoder(private val secret: ByteArray) : EncryptedCoder() {
    fun factoryFor(file: File): InputStreamFactory {
      return object : InputStreamFactory {
        override fun create(): InputStream = createEncryptedInputStream(secret, file)
      }
    }
  }
}
