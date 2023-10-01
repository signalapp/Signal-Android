/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.glide.webp

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.LibraryGlideModule
import org.signal.core.util.logging.Log
import java.io.InputStream

/**
 * Registers a custom handler for webp images.
 */
@GlideModule
class WebpLibraryGlideModule : LibraryGlideModule() {
  companion object {
    private val TAG = Log.tag(WebpLibraryGlideModule::class.java)
  }

  override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
    Log.d(TAG, "registerComponents()")
    registry.prepend(InputStream::class.java, Bitmap::class.java, WebpInputStreamResourceDecoder(glide.bitmapPool))
  }
}
