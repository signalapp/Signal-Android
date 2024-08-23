/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow
import org.thoughtcrime.securesms.video.TranscodingPreset
import org.thoughtcrime.securesms.video.TranscodingQuality
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter
import java.util.UUID
import kotlin.math.roundToInt

/**
 * ViewModel for the transcoding screen of the video sample app. See [TranscodeTestActivity].
 */
class TranscodeTestViewModel : ViewModel() {
  private lateinit var repository: TranscodeTestRepository
  private var backPressedRunnable = {}
  private var transcodingJobs: Map<UUID, Uri> = emptyMap()

  var transcodingPreset by mutableStateOf(TranscodingPreset.LEVEL_2)
    private set

  var outputDirectory: Uri? by mutableStateOf(null)
    private set

  var selectedVideos: List<Uri> by mutableStateOf(emptyList())
  var videoMegaBitrate by mutableFloatStateOf(calculateVideoMegaBitrateFromPreset(transcodingPreset))
  var videoResolution by mutableStateOf(convertPresetToVideoResolution(transcodingPreset))
  var audioKiloBitrate by mutableIntStateOf(calculateAudioKiloBitrateFromPreset(transcodingPreset))
  var useHevc by mutableStateOf(false)
  var useAutoTranscodingSettings by mutableStateOf(true)
  var enableFastStart by mutableStateOf(true)
  var enableAudioRemux by mutableStateOf(true)
  var forceSequentialQueueProcessing by mutableStateOf(false)

  fun initialize(context: Context) {
    repository = TranscodeTestRepository(context)
    backPressedRunnable = { Toast.makeText(context, "Cancelling all transcoding jobs!", Toast.LENGTH_LONG).show() }
  }

  fun transcode() {
    val output = outputDirectory ?: throw IllegalStateException("No output directory selected!")
    transcodingJobs = if (useAutoTranscodingSettings) {
      repository.transcodeWithPresetOptions(
        selectedVideos,
        output,
        forceSequentialQueueProcessing,
        transcodingPreset
      )
    } else {
      repository.transcodeWithCustomOptions(
        selectedVideos,
        output,
        forceSequentialQueueProcessing,
        TranscodeTestRepository.CustomTranscodingOptions(
          if (useHevc) MediaConverter.VIDEO_CODEC_H265 else MediaConverter.VIDEO_CODEC_H264,
          videoResolution,
          (videoMegaBitrate * MEGABIT).roundToInt(),
          audioKiloBitrate * KILOBIT,
          enableAudioRemux,
          enableFastStart
        )
      )
    }
  }

  fun updateTranscodingPreset(preset: TranscodingPreset) {
    transcodingPreset = preset
    videoResolution = convertPresetToVideoResolution(preset)
    videoMegaBitrate = calculateVideoMegaBitrateFromPreset(preset)
    audioKiloBitrate = calculateAudioKiloBitrateFromPreset(preset)
  }

  fun getTranscodingJobsAsState(): Flow<MutableList<WorkInfo>> {
    return repository.getTranscodingJobsAsFlow(transcodingJobs.keys.toList())
  }

  fun setOutputDirectoryAndCleanFailedTranscodes(context: Context, folderUri: Uri) {
    outputDirectory = folderUri
    repository.cleanPrivateStorage(context)
  }

  fun reset() {
    cancelAllTranscodes()
    resetOutputDirectory()
    selectedVideos = emptyList()
  }

  private fun cancelAllTranscodes() {
    repository.cancelAllTranscodes()
    transcodingJobs = emptyMap()
  }

  fun resetOutputDirectory() {
    outputDirectory = null
  }

  companion object {
    private const val MEGABIT = 1000000
    private const val KILOBIT = 1000

    @JvmStatic
    private fun calculateVideoMegaBitrateFromPreset(preset: TranscodingPreset): Float {
      val quality = TranscodingQuality.createFromPreset(preset, -1)
      return quality.targetVideoBitRate.toFloat() / MEGABIT
    }

    @JvmStatic
    private fun calculateAudioKiloBitrateFromPreset(preset: TranscodingPreset): Int {
      val quality = TranscodingQuality.createFromPreset(preset, -1)
      return quality.targetAudioBitRate / KILOBIT
    }

    @JvmStatic
    private fun convertPresetToVideoResolution(preset: TranscodingPreset) = when (preset) {
      TranscodingPreset.LEVEL_3 -> VideoResolution.HD
      else -> VideoResolution.SD
    }
  }
}
