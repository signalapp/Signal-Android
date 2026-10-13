/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.signal.core.util.logging.Log
import org.signal.mediasend.MediaSendDependencies
import org.thoughtcrime.securesms.video.interfaces.MediaInput
import org.thoughtcrime.securesms.video.videoconverter.VideoThumbnailsExtractor
import java.io.IOException

/**
 * Decodes evenly spaced frames of a video for the trim bar's thumbnail strip.
 */
object VideoThumbnailRepository {

  private val TAG = Log.tag(VideoThumbnailRepository::class)

  /**
   * Emits the video's duration as soon as it is read, then each of the [count] thumbnails as it is decoded, with its
   * shorter side [resolutionPx] long. Completes without a duration if the video cannot be read.
   *
   * The whole decode runs on one thread, since the extractor renders through an EGL context bound to it. Cancelling
   * collection stops the decode at the next frame.
   */
  fun thumbnails(uri: Uri, count: Int, resolutionPx: Int): Flow<VideoThumbnailResult> {
    return channelFlow {
      val input = openInput(uri) ?: return@channelFlow

      try {
        VideoThumbnailsExtractor.extractThumbnails(
          input,
          count,
          resolutionPx,
          object : VideoThumbnailsExtractor.Callback {
            override fun durationKnown(duration: Long) {
              trySendBlocking(VideoThumbnailResult.DurationKnown(duration))
            }

            override fun publishProgress(index: Int, thumbnail: Bitmap): Boolean {
              if (!isActive) {
                thumbnail.recycle()
                return false
              }

              val upright = thumbnail.rotatedHalfTurn()
              val sent = trySendBlocking(VideoThumbnailResult.Thumbnail(index, upright.asImageBitmap())).isSuccess
              if (!sent) {
                upright.recycle()
              }
              return sent
            }

            override fun failed() {
              Log.w(TAG, "Thumbnail extraction failed.")
            }
          }
        )
      } finally {
        input.close()
      }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.Default)
  }

  private fun openInput(uri: Uri): MediaInput? {
    return try {
      MediaSendDependencies.mediaInputFactory.createForUri(MediaSendDependencies.application, uri)
    } catch (e: IOException) {
      Log.w(TAG, "Unable to open the video for thumbnails.", e)
      null
    }
  }

  /** Frames are read back from GL upside down. */
  private fun Bitmap.rotatedHalfTurn(): Bitmap {
    val matrix = Matrix().apply { postRotate(180f) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, false)
    if (rotated !== this) {
      recycle()
    }
    return rotated
  }
}

sealed interface VideoThumbnailResult {
  data class DurationKnown(val durationUs: Long) : VideoThumbnailResult
  data class Thumbnail(val index: Int, val bitmap: ImageBitmap) : VideoThumbnailResult
}
