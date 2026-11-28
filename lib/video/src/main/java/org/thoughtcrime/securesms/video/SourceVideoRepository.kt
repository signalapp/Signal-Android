/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video

import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import org.signal.core.util.logging.Log
import java.io.IOException

/**
 * Reads properties of a video that is about to be transcoded.
 */
object SourceVideoRepository {
  private val TAG = Log.tag(SourceVideoRepository::class.java)

  private const val KEY_DISPLAY_WIDTH = "display-width"
  private const val KEY_DISPLAY_HEIGHT = "display-height"

  @JvmStatic
  @RequiresApi(26)
  fun readLimits(dataSource: MediaDataSource, sizeBytes: Long, durationMs: Long): SourceVideoLimits {
    val extractor = MediaExtractor()
    return try {
      extractor.setDataSource(dataSource)

      val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
      val videoFormat = formats.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
      val audioFormat = formats.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }

      createLimits(
        width = videoFormat?.positiveInt(KEY_DISPLAY_WIDTH) ?: videoFormat?.positiveInt(MediaFormat.KEY_WIDTH),
        height = videoFormat?.positiveInt(KEY_DISPLAY_HEIGHT) ?: videoFormat?.positiveInt(MediaFormat.KEY_HEIGHT),
        videoTrackBitRate = videoFormat?.positiveInt(MediaFormat.KEY_BIT_RATE),
        audioTrackBitRate = audioFormat?.positiveInt(MediaFormat.KEY_BIT_RATE),
        hasAudioTrack = audioFormat != null,
        totalBitRate = TranscodingQuality.bitRate(sizeBytes, durationMs)
      )
    } catch (e: IOException) {
      Log.w(TAG, "Unable to read source video properties", e)
      SourceVideoLimits.UNKNOWN
    } catch (e: RuntimeException) {
      Log.w(TAG, "Unable to read source video properties", e)
      SourceVideoLimits.UNKNOWN
    } finally {
      extractor.release()
    }
  }

  /**
   * Video tracks rarely declare a bitrate, so when it is missing it is estimated as whatever the audio track does not account for.
   */
  @VisibleForTesting
  internal fun createLimits(width: Int?, height: Int?, videoTrackBitRate: Int?, audioTrackBitRate: Int?, hasAudioTrack: Boolean, totalBitRate: Int): SourceVideoLimits {
    val estimatedVideoBitRate = (totalBitRate - (audioTrackBitRate ?: 0)).takeIf { it > 0 }

    return SourceVideoLimits(
      shortEdge = if (width != null && height != null) minOf(width, height) else null,
      videoBitRate = videoTrackBitRate ?: estimatedVideoBitRate,
      audioBitRate = if (hasAudioTrack) audioTrackBitRate else 0
    )
  }

  private fun MediaFormat.positiveInt(key: String): Int? {
    return if (containsKey(key)) getInteger(key).takeIf { it > 0 } else null
  }
}
