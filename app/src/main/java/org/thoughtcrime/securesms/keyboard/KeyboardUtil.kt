/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyboard

import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.util.component1
import androidx.core.util.component2
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.BitmapUtil
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

object KeyboardUtil {

  @WorkerThread
  fun getImageDetails(uri: Uri): ImageDetails? {
    return try {
      val (width, height) = BitmapUtil.getDimensions(AppDependencies.application.contentResolver.openInputStream(uri))
      return ImageDetails(width = width, height = height, isSticker = uri.isForSticker())
    } catch (e: InterruptedException) {
      null
    } catch (e: ExecutionException) {
      null
    } catch (e: TimeoutException) {
      null
    }
  }

  private fun Uri.isForSticker(): Boolean {
    val string = this.toString()
    return string.contains("sticker") || string.contains("com.touchtype.swiftkey.fileprovider/share_images")
  }

  data class ImageDetails(val width: Int, val height: Int, val isSticker: Boolean)
}
