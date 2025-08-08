/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mms

import android.content.Context
import android.net.Uri
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool
import com.bumptech.glide.load.resource.bitmap.RecyclableBufferedInputStream
import org.signal.core.util.logging.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

interface InputStreamFactory {
  companion object {
    @JvmStatic
    fun build(context: Context, uri: Uri): InputStreamFactory = UriInputStreamFactory(context, uri)

    @JvmStatic
    fun build(file: File): InputStreamFactory = FileInputStreamFactory(file)
  }

  fun create(): InputStream
  fun createRecyclable(byteArrayPool: ArrayPool): InputStream = RecyclableBufferedInputStream(create(), byteArrayPool)
}

/**
 * A factory that creates a new [InputStream] for the given [Uri] each time [create] is called.
 */
class UriInputStreamFactory(
  private val context: Context,
  private val uri: Uri
) : InputStreamFactory {
  companion object {
    private val TAG = Log.tag(UriInputStreamFactory::class)
  }

  override fun create(): InputStream {
    return try {
      DecryptableStreamLocalUriFetcher(context, uri).loadResource(uri, context.contentResolver)
    } catch (e: Exception) {
      Log.w(TAG, "Error creating input stream for URI.", e)
      throw e
    }
  }
}

/**
 * A factory that creates a new [InputStream] for the given [File] each time [create] is called.
 */
class FileInputStreamFactory(
  private val file: File
) : InputStreamFactory {
  companion object {
    private val TAG = Log.tag(FileInputStreamFactory::class)
  }

  override fun create(): InputStream {
    return try {
      FileInputStream(file)
    } catch (e: Exception) {
      Log.w(TAG, "Error creating input stream for File.", e)
      throw e
    }
  }
}
