/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyboard

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.WorkerThread
import org.signal.core.util.bitmaps.BitmapUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import kotlin.math.max
import kotlin.math.min

object KeyboardUtil {

  private val TAG = Log.tag(KeyboardUtil::class)

  /** Keyboard stickers are small, roughly square images with transparency. Larger or elongated images are photos or screenshots. */
  private const val STICKER_MAX_DIMENSION = 1024
  private const val STICKER_MAX_ASPECT_RATIO = 1.5f

  @WorkerThread
  fun getImageDetails(uri: Uri): ImageDetails? {
    return try {
      val resolver = AppDependencies.application.contentResolver
      val (width, height) = BitmapUtil.getDimensions(resolver.openInputStream(uri))
      val isSticker = uri.isForSticker() || (hasStickerDimensions(width, height) && hasAlpha(resolver, uri))
      ImageDetails(width = width, height = height, isSticker = isSticker)
    } catch (e: Exception) {
      Log.w(TAG, "Unable to read details for the provided image.", e)
      null
    }
  }

  /** Fast path for keyboards whose URIs say so. Everything else is decided by the content check. */
  private fun Uri.isForSticker(): Boolean {
    val string = this.toString()
    return string.contains("sticker") || string.contains("com.touchtype.swiftkey.fileprovider/share_images")
  }

  private fun hasStickerDimensions(width: Int, height: Int): Boolean {
    if (width <= 0 || height <= 0) {
      return false
    }
    val longSide = max(width, height)
    val shortSide = min(width, height)
    return longSide <= STICKER_MAX_DIMENSION && longSide.toFloat() / shortSide <= STICKER_MAX_ASPECT_RATIO
  }

  /** Reads only the file header (PNG IHDR/tRNS, WebP VP8X/VP8L flags). GIFs are not inspected and keep relying on the URI fast path. */
  private fun hasAlpha(resolver: ContentResolver, uri: Uri): Boolean {
    return resolver.openInputStream(uri)?.let { ImageAlphaSniffer.hasAlpha(it) } ?: false
  }

  data class ImageDetails(val width: Int, val height: Int, val isSticker: Boolean)
}
