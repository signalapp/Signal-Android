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
import org.signal.mediasend.edit.video.VideoTrimData

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
    val videoTrimData: VideoTrimData
  ) : EditorState {

    val clipDurationUs: Long get() = videoTrimData.endTimeUs - videoTrimData.startTimeUs

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

      return VideoTrim(
        videoTrimData = videoTrimData.copy(
          isDurationEdited = true,
          startTimeUs = if (!preserveStartTime) videoTrimData.endTimeUs - maxDurationUs else videoTrimData.startTimeUs,
          endTimeUs = if (preserveStartTime) videoTrimData.startTimeUs + maxDurationUs else videoTrimData.endTimeUs
        )
      )
    }

    companion object {
      private const val KEY_MODEL = "model"

      fun fromBundle(bundle: Bundle): VideoTrim {
        return VideoTrim(
          videoTrimData = bundle.getParcelableCompat(KEY_MODEL, VideoTrimData::class.java)!!
        )
      }

      /**
       * Creates initial trim data for a video, clamping to max duration if needed.
       */
      fun forVideo(durationUs: Long, maxDurationUs: Long): VideoTrim {
        val videoTrimData = if (durationUs <= maxDurationUs) {
          VideoTrimData(
            isDurationEdited = false,
            totalInputDurationUs = durationUs,
            startTimeUs = 0,
            endTimeUs = durationUs
          )
        } else {
          VideoTrimData(
            isDurationEdited = true,
            totalInputDurationUs = durationUs,
            startTimeUs = 0,
            endTimeUs = maxDurationUs
          )
        }

        return VideoTrim(videoTrimData = videoTrimData)
      }
    }

    fun toBundle(): Bundle = Bundle().apply {
      putParcelable(KEY_MODEL, videoTrimData)
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
