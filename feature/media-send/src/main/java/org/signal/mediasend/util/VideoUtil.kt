/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.util

import org.signal.mediasend.MediaConstraints
import org.signal.mediasend.MediaSendDependencies
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import kotlin.math.floor
import kotlin.math.min

object VideoUtil {

  /** Recordings that fall back to a RAM-backed file descriptor keep the historical duration cap. */
  private const val MAX_IN_MEMORY_RECORD_DURATION_SECONDS = 60

  /**
   * The recording cap for a RAM-backed file descriptor, bounded by how much compressed video fits in
   * the memory file.
   */
  fun getMemoryBackedMaxRecordDurationSeconds(mediaConstraints: MediaConstraints): Int {
    val config = VideoConstants.DEFAULT_HIGH
    val bytesPerSecond = (config.videoBitrateMbps * VideoConstants.MB).toInt() / 8 + (config.audioBitrateKbps * VideoConstants.KB) / 8
    val duration = floor(mediaConstraints.compressedVideoMaxSize.toFloat() / bytesPerSecond).toInt()

    return min(duration, MAX_IN_MEMORY_RECORD_DURATION_SECONDS)
  }

  /**
   * The recording cap for a disk-backed file descriptor, which is bounded only by the longest
   * duration the transcoder will accept.
   */
  fun getDiskBackedMaxRecordDurationSeconds(): Int {
    return MediaSendDependencies.mediaSendRepository.getMaxVideoRecordDurationSeconds()
  }
}
