/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyboard

import android.net.Uri
import androidx.annotation.WorkerThread
import org.signal.core.util.bitmaps.BitmapUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies

object KeyboardUtil {

  private val TAG = Log.tag(KeyboardUtil::class)

  @WorkerThread
  fun getImageDetails(uri: Uri): ImageDetails? {
    return try {
      val (width, height) = BitmapUtil.getDimensions(AppDependencies.application.contentResolver.openInputStream(uri))
      ImageDetails(width = width, height = height, isSticker = uri.isForSticker())
    } catch (e: Exception) {
      Log.w(TAG, "Unable to read details for the provided image.", e)
      null
    }
  }

  private fun Uri.isForSticker(): Boolean {
    val string = this.toString()
    return string.contains("sticker") || string.contains("com.touchtype.swiftkey.fileprovider/share_images")
  }

  data class ImageDetails(val width: Int, val height: Int, val isSticker: Boolean)
}
