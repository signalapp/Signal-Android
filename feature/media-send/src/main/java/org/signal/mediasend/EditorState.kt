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
import org.signal.mediasend.screens.edit.video.VideoTrimData

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
    val videoTrimData: VideoTrimData,
    val maxDurationUs: Long = 0
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
        return copy(maxDurationUs = maxDurationUs)
      }

      return VideoTrim(
        videoTrimData = videoTrimData.copy(
          isDurationEdited = true,
          startTimeUs = if (!preserveStartTime) videoTrimData.endTimeUs - maxDurationUs else videoTrimData.startTimeUs,
          endTimeUs = if (preserveStartTime) videoTrimData.startTimeUs + maxDurationUs else videoTrimData.endTimeUs
        ),
        maxDurationUs = maxDurationUs
      )
    }

    companion object {
      private const val KEY_MODEL = "model"
      private const val KEY_MAX_DURATION = "max_duration"

      fun fromBundle(bundle: Bundle): VideoTrim {
        return VideoTrim(
          videoTrimData = bundle.getParcelableCompat(KEY_MODEL, VideoTrimData::class.java)!!,
          maxDurationUs = bundle.getLong(KEY_MAX_DURATION)
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

        return VideoTrim(videoTrimData = videoTrimData, maxDurationUs = maxDurationUs)
      }
    }

    fun toBundle(): Bundle = Bundle().apply {
      putParcelable(KEY_MODEL, videoTrimData)
      putLong(KEY_MAX_DURATION, maxDurationUs)
    }
  }

  /**
   * Gif video state. Gif videos loop silently and cannot be trimmed, so there is nothing to track beyond the type
   * itself.
   */
  @Parcelize
  data object VideoGif : EditorState

  /**
   * Animated gif state. Gifs are played back as-is rather than being routed through the image editor, so there is
   * nothing to track beyond the type itself.
   */
  @Parcelize
  data object Gif : EditorState

  /**
   * Document state. Documents cannot be edited, so we hold onto what we need to describe the file to the user.
   */
  @Parcelize
  data class Document(
    val fileName: String?,
    val fileSize: Long,
    val extension: String
  ) : EditorState

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
