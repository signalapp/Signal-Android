/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.models.media.Media
import org.signal.core.util.ContentTypeUtil
import org.signal.core.util.SeekableFileDescriptor
import org.signal.core.util.contentproviders.BlobProvider
import org.signal.mediasend.MediaSendDependencies
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import java.io.FileInputStream
import java.io.IOException

/**
 * Turns what the camera hands back into media the rest of the flow can work with, by writing it to a single-session
 * blob. A recording's dimensions are left at zero, since those are only known once population has probed the file.
 */
internal class MediaCaptureRepository(
  private val context: Context = MediaSendDependencies.application,
  private val blobs: BlobProvider = MediaSendDependencies.blobs
) {

  /** @return The captured image, or null if it could not be written out. */
  suspend fun writeCapturedImage(data: ByteArray, width: Int, height: Int): Media? = withContext(Dispatchers.IO) {
    try {
      val uri = blobs
        .forData(data)
        .withMimeType(ContentTypeUtil.IMAGE_JPEG)
        .createForSingleSessionOnDisk(context)

      buildCapturedMedia(uri, ContentTypeUtil.IMAGE_JPEG, width, height, data.size.toLong())
    } catch (e: IOException) {
      null
    }
  }

  /**
   * @param fd The recording, which is closed here whether or not it could be written out.
   * @return The captured recording, or null if it could not be written out.
   */
  suspend fun writeCapturedVideo(fd: SeekableFileDescriptor): Media? = withContext(Dispatchers.IO) {
    try {
      fd.use { descriptor ->
        FileInputStream(descriptor.fileDescriptor).use { stream ->
          val length = stream.channel.size()
          val uri = blobs
            .forData(stream, length)
            .withMimeType(VideoConstants.RECORDED_VIDEO_CONTENT_TYPE)
            .createForSingleSessionOnDisk(context)

          buildCapturedMedia(uri, VideoConstants.RECORDED_VIDEO_CONTENT_TYPE, 0, 0, length)
        }
      }
    } catch (e: IOException) {
      null
    }
  }

  private fun buildCapturedMedia(uri: Uri, mimeType: String, width: Int, height: Int, size: Long): Media {
    return Media(
      uri = uri,
      contentType = mimeType,
      date = System.currentTimeMillis(),
      width = width,
      height = height,
      size = size,
      duration = 0,
      isBorderless = false,
      isVideoGif = false,
      bucketId = Media.ALL_MEDIA_BUCKET_ID,
      caption = null,
      transformProperties = null,
      fileName = null
    )
  }
}
