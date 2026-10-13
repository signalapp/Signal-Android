/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.util

import android.content.Context
import org.signal.camera.CameraDependencies
import org.signal.core.util.Util
import org.signal.mediasend.MediaSendDependencies
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants

object VideoUtil {

  /** Recordings that fall back to a RAM-backed file descriptor keep the historical duration cap on devices that can least afford the memory. */
  const val MAX_IN_MEMORY_RECORD_DURATION_SECONDS_LOW_MEMORY = 60

  /** The RAM-backed cap everywhere else. */
  private const val MAX_IN_MEMORY_RECORD_DURATION_SECONDS = 300

  /**
   * The recording cap for a RAM-backed file descriptor.
   */
  fun getMemoryBackedMaxRecordDurationSeconds(context: Context): Int {
    return if (Util.isLowMemory(context)) MAX_IN_MEMORY_RECORD_DURATION_SECONDS_LOW_MEMORY else MAX_IN_MEMORY_RECORD_DURATION_SECONDS
  }

  /**
   * How much RAM a memory-backed recording of [durationSeconds] needs, derived from the bitrate the recorder is
   * actually pinned to rather than a fixed guess.
   */
  fun getMemoryBackedRecordSizeBytes(durationSeconds: Int): Long {
    val bytesPerSecond = (CameraDependencies.getMaxVideoBitrateBps() + VideoConstants.DEFAULT_HIGH.audioBitrateKbps * VideoConstants.KB) / 8
    return durationSeconds.toLong() * bytesPerSecond
  }

  /**
   * The recording cap for a disk-backed file descriptor, which is bounded only by the longest
   * duration the transcoder will accept.
   */
  fun getDiskBackedMaxRecordDurationSeconds(): Int {
    return MediaSendDependencies.mediaSendRepository.getMaxVideoRecordDurationSeconds()
  }
}
