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
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter
import kotlin.math.roundToInt

/**
 * ViewModel for the video transcode demo app.
 */
class TranscodeTestViewModel : ViewModel() {
  private val repository = TranscodeTestRepository()
  private var transcodeJob: Job? = null

  var selectedVideo: Uri? by mutableStateOf(null)

  var transcodeQuality by mutableStateOf(TranscodeQuality.STANDARD)
    private set

  var videoMegaBitrate by mutableFloatStateOf(calculateVideoMegaBitrateFromQuality(transcodeQuality))
  var videoResolution by mutableStateOf(convertQualityToVideoResolution(transcodeQuality))
  var audioKiloBitrate by mutableIntStateOf(calculateAudioKiloBitrateFromQuality(transcodeQuality))
  var useAutoTranscodingSettings by mutableStateOf(true)
  var enableFastStart by mutableStateOf(true)
  var enableAudioRemux by mutableStateOf(true)

  private val _transcodingState = MutableStateFlow<TranscodingState>(TranscodingState.Idle)
  val transcodingState: StateFlow<TranscodingState> = _transcodingState.asStateFlow()

  fun updateTranscodeQuality(quality: TranscodeQuality) {
    transcodeQuality = quality
    videoResolution = convertQualityToVideoResolution(quality)
    videoMegaBitrate = calculateVideoMegaBitrateFromQuality(quality)
    audioKiloBitrate = calculateAudioKiloBitrateFromQuality(quality)
  }

  fun startTranscode(context: Context) {
    val video = selectedVideo ?: return
    _transcodingState.value = TranscodingState.InProgress(0)

    val settings = TranscodeSettings(
      quality = if (useAutoTranscodingSettings) transcodeQuality else null,
      videoResolution = videoResolution,
      videoMegaBitrate = videoMegaBitrate,
      audioKiloBitrate = audioKiloBitrate,
      enableFastStart = enableFastStart,
      enableAudioRemux = enableAudioRemux
    )

    transcodeJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val result = if (useAutoTranscodingSettings) {
          repository.transcodeWithQuality(
            context = context,
            inputUri = video,
            quality = transcodeQuality,
            enableFastStart = enableFastStart,
            enableAudioRemux = enableAudioRemux,
            onProgress = { percent -> _transcodingState.value = TranscodingState.InProgress(percent) }
          )
        } else {
          repository.transcodeWithCustomOptions(
            context = context,
            inputUri = video,
            options = TranscodeTestRepository.CustomTranscodingOptions(
              videoCodec = MediaConverter.VIDEO_CODEC_H264,
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
  }
}
