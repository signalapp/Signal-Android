/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.video.TranscodingPreset
import org.thoughtcrime.securesms.video.TranscodingQuality
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter
import kotlin.math.roundToInt

/**
 * ViewModel for the video transcode demo app.
 */
class TranscodeTestViewModel : ViewModel() {
  private val repository = TranscodeTestRepository()
  private var transcodeJob: Job? = null

  var selectedVideo: Uri? by mutableStateOf(null)

  var transcodingPreset by mutableStateOf(TranscodingPreset.LEVEL_2)
    private set

  var videoMegaBitrate by mutableFloatStateOf(calculateVideoMegaBitrateFromPreset(transcodingPreset))
  var videoResolution by mutableStateOf(convertPresetToVideoResolution(transcodingPreset))
  var audioKiloBitrate by mutableIntStateOf(calculateAudioKiloBitrateFromPreset(transcodingPreset))
  var useHevc by mutableStateOf(false)
  var useAutoTranscodingSettings by mutableStateOf(true)
  var enableFastStart by mutableStateOf(true)
  var enableAudioRemux by mutableStateOf(true)

  private val _transcodingState = MutableStateFlow<TranscodingState>(TranscodingState.Idle)
  val transcodingState: StateFlow<TranscodingState> = _transcodingState.asStateFlow()

  fun updateTranscodingPreset(preset: TranscodingPreset) {
    transcodingPreset = preset
    videoResolution = convertPresetToVideoResolution(preset)
    videoMegaBitrate = calculateVideoMegaBitrateFromPreset(preset)
    audioKiloBitrate = calculateAudioKiloBitrateFromPreset(preset)
  }

  fun startTranscode(context: Context) {
    val video = selectedVideo ?: return
    _transcodingState.value = TranscodingState.InProgress(0)

    val settings = TranscodeSettings(
      isPreset = useAutoTranscodingSettings,
      presetName = if (useAutoTranscodingSettings) transcodingPreset.name else null,
      videoResolution = videoResolution,
      videoMegaBitrate = videoMegaBitrate,
      audioKiloBitrate = audioKiloBitrate,
      useHevc = useHevc,
      enableFastStart = enableFastStart,
      enableAudioRemux = enableAudioRemux
    )

    transcodeJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val result = if (useAutoTranscodingSettings) {
          repository.transcodeWithPreset(
            context = context,
            inputUri = video,
            preset = transcodingPreset,
            enableFastStart = enableFastStart,
            enableAudioRemux = enableAudioRemux,
            onProgress = { percent -> _transcodingState.value = TranscodingState.InProgress(percent) }
          )
        } else {
          repository.transcodeWithCustomOptions(
            context = context,
            inputUri = video,
            options = TranscodeTestRepository.CustomTranscodingOptions(
              videoCodec = if (useHevc) MediaConverter.VIDEO_CODEC_H265 else MediaConverter.VIDEO_CODEC_H264,
              videoResolution = videoResolution,
              videoBitrate = (videoMegaBitrate * MEGABIT).roundToInt(),
              audioBitrate = audioKiloBitrate * KILOBIT,
              enableFastStart = enableFastStart,
              enableAudioRemux = enableAudioRemux
            ),
            onProgress = { percent -> _transcodingState.value = TranscodingState.InProgress(percent) }
          )
        }
        _transcodingState.value = TranscodingState.Completed(
          outputUri = result.outputUri,
          originalFile = result.originalFile,
          originalSize = result.originalSize,
          outputSize = result.outputSize,
          settings = settings
        )
      } catch (e: CancellationException) {
        _transcodingState.value = TranscodingState.Cancelled
      } catch (e: Exception) {
        _transcodingState.value = TranscodingState.Failed(e.message ?: "Unknown error")
      }
    }
  }

  fun cancelTranscode() {
    transcodeJob?.cancel()
  }

  fun reset() {
    cancelTranscode()
    val currentState = _transcodingState.value
    if (currentState is TranscodingState.Completed) {
      currentState.originalFile.delete()
    }
    selectedVideo = null
    _transcodingState.value = TranscodingState.Idle
  }

  companion object {
    private const val MEGABIT = 1_000_000
    private const val KILOBIT = 1_000

    private fun calculateVideoMegaBitrateFromPreset(preset: TranscodingPreset): Float {
      val quality = TranscodingQuality.createFromPreset(preset, -1)
      return quality.targetVideoBitRate.toFloat() / MEGABIT
    }

    private fun calculateAudioKiloBitrateFromPreset(preset: TranscodingPreset): Int {
      val quality = TranscodingQuality.createFromPreset(preset, -1)
      return quality.targetAudioBitRate / KILOBIT
    }

    private fun convertPresetToVideoResolution(preset: TranscodingPreset) = when (preset) {
      TranscodingPreset.LEVEL_3, TranscodingPreset.LEVEL_3_H265 -> VideoResolution.HD
      else -> VideoResolution.SD
    }
  }
}
