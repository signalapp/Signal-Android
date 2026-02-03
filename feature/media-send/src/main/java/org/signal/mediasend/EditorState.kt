/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.bundleOf
import kotlinx.parcelize.Parcelize
import org.signal.core.util.getParcelableCompat
import org.signal.imageeditor.core.model.EditorModel

/**
 * Sealed interface for per-media editor state. All subtypes are [Parcelable] so the
 * entire editor state map can be persisted in [SavedStateHandle].
 */
sealed interface EditorState : Parcelable {

  /**
   * Video trim/duration editing state.
   */
  @Parcelize
  data class VideoTrim(
    val isDurationEdited: Boolean = false,
    val totalInputDurationUs: Long = 0,
    val startTimeUs: Long = 0,
    val endTimeUs: Long = 0
  ) : EditorState {

    val clipDurationUs: Long get() = endTimeUs - startTimeUs

    /**
     * Clamps this trim data to the maximum allowed clip duration.
     *
     * @param maxDurationUs Maximum allowed duration in microseconds.
     * @param preserveStartTime If true, keeps start time and adjusts end; otherwise adjusts start.
     * @return Clamped VideoTrim, or this if already within limits.
     */
    fun clampToMaxDuration(maxDurationUs: Long, preserveStartTime: Boolean): VideoTrim {
      if (clipDurationUs <= maxDurationUs) {
        return this
      }

      return copy(
        isDurationEdited = true,
        startTimeUs = if (!preserveStartTime) endTimeUs - maxDurationUs else startTimeUs,
        endTimeUs = if (preserveStartTime) startTimeUs + maxDurationUs else endTimeUs
      )
    }

    companion object {
      private const val KEY_IS_DURATION_EDITED = "isDurationEdited"
      private const val KEY_TOTAL_INPUT_DURATION_US = "totalInputDurationUs"
      private const val KEY_START_TIME_US = "startTimeUs"
      private const val KEY_END_TIME_US = "endTimeUs"

      fun fromBundle(bundle: Bundle): VideoTrim {
        return VideoTrim(
          isDurationEdited = bundle.getBoolean(KEY_IS_DURATION_EDITED, false),
          totalInputDurationUs = bundle.getLong(KEY_TOTAL_INPUT_DURATION_US, 0),
          startTimeUs = bundle.getLong(KEY_START_TIME_US, 0),
          endTimeUs = bundle.getLong(KEY_END_TIME_US, 0)
        )
      }

      /**
       * Creates initial trim data for a video, clamping to max duration if needed.
       */
      fun forVideo(durationUs: Long, maxDurationUs: Long): VideoTrim {
        return if (durationUs <= maxDurationUs) {
          VideoTrim(
            isDurationEdited = false,
            totalInputDurationUs = durationUs,
            startTimeUs = 0,
            endTimeUs = durationUs
          )
        } else {
          VideoTrim(
            isDurationEdited = true,
            totalInputDurationUs = durationUs,
            startTimeUs = 0,
            endTimeUs = maxDurationUs
          )
        }
      }
    }

    fun toBundle(): Bundle = Bundle().apply {
      putBoolean(KEY_IS_DURATION_EDITED, isDurationEdited)
      putLong(KEY_TOTAL_INPUT_DURATION_US, totalInputDurationUs)
      putLong(KEY_START_TIME_US, startTimeUs)
      putLong(KEY_END_TIME_US, endTimeUs)
    }
  }

  /**
   * Image editor state.
   */
  @Parcelize
  data class Image(
    val model: EditorModel
  ) : EditorState {
    companion object {
      private const val KEY_MODEL = "model"

      fun fromBundle(bundle: Bundle): Image = Image(bundle.getParcelableCompat(KEY_MODEL, EditorModel::class.java)!!)
    }

    fun toBundle(): Bundle = bundleOf(KEY_MODEL to model)
  }
}
