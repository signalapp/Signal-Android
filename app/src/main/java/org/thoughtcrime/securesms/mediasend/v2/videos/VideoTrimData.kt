/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v2.videos

import android.os.Bundle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

/**
 * This represents the editor state for the video trimmer.
 */
data class VideoTrimData(
  val isDurationEdited: Boolean = false,
  val totalInputDurationUs: Long = 0,
  val startTimeUs: Long = 0,
  val endTimeUs: Long = 0
) {

  fun getDuration(): Duration = (endTimeUs - startTimeUs).microseconds

  fun toBundle(): Bundle {
    return Bundle().apply {
      putByte(KEY_EDITED, (if (isDurationEdited) 1 else 0).toByte())
      putLong(KEY_TOTAL, totalInputDurationUs)
      putLong(KEY_START, startTimeUs)
      putLong(KEY_END, endTimeUs)
    }
  }

  companion object {
    private const val KEY_EDITED = "EDITED"
    private const val KEY_TOTAL = "TOTAL"
    private const val KEY_START = "START"
    private const val KEY_END = "END"

    fun fromBundle(bundle: Bundle): VideoTrimData {
      return VideoTrimData(
        isDurationEdited = bundle.getByte(KEY_EDITED) == 1.toByte(),
        totalInputDurationUs = bundle.getLong(KEY_TOTAL),
        startTimeUs = bundle.getLong(KEY_START),
        endTimeUs = bundle.getLong(KEY_END)
      )
    }
  }
}
