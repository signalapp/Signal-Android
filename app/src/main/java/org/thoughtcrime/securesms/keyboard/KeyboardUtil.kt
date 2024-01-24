/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyboard

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.annotation.WorkerThread
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object KeyboardUtil {

  @WorkerThread
  fun getImageDetails(requestManager: RequestManager, uri: Uri): ImageDetails? {
    return try {
      val bitmap: Bitmap = requestManager.asBitmap()
        .load(DecryptableStreamUriLoader.DecryptableUri(uri))
        .skipMemoryCache(true)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .submit()
        .get(1000, TimeUnit.MILLISECONDS)
      val topLeft = bitmap.getPixel(0, 0)
      ImageDetails(bitmap.width, bitmap.height, Color.alpha(topLeft) < 255)
    } catch (e: InterruptedException) {
      null
    } catch (e: ExecutionException) {
      null
    } catch (e: TimeoutException) {
      null
    }
  }

  data class ImageDetails(val width: Int, val height: Int, val hasTransparency: Boolean)
}
