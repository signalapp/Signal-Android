/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.batch

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter
import org.thoughtcrime.video.app.transcode.TranscodeSettings
import org.thoughtcrime.video.app.transcode.TranscodeTestRepository
import java.io.File
import kotlin.math.roundToInt

class BatchTranscodeViewModel : ViewModel() {
  private val repository = TranscodeTestRepository()
  private var batchJob: Job? = null

  val profiles = mutableStateListOf<TranscodeSettings>()

  val selectedVideos = mutableStateListOf<SelectedVideo>()

  private val playbackFiles = mutableListOf<File>()

  private val _batchState = MutableStateFlow<BatchTranscodingState>(BatchTranscodingState.Idle)
  val batchState: StateFlow<BatchTranscodingState> = _batchState.asStateFlow()

  fun addProfile(settings: TranscodeSettings) {
    profiles.add(settings)
  }

  fun removeProfile(index: Int) {
    if (index in profiles.indices) {
      profiles.removeAt(index)
    }
  }

  fun setVideos(context: Context, uris: List<Uri>) {
    viewModelScope.launch(Dispatchers.IO) {
      val videos = uris.map { uri -> SelectedVideo(uri = uri, name = queryDisplayName(context, uri)) }
      selectedVideos.clear()
      selectedVideos.addAll(videos)
    }
  }

  fun removeVideo(index: Int) {
    if (index in selectedVideos.indices) {
      selectedVideos.removeAt(index)
    }
  }

  private fun queryDisplayName(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: "unknown"
  }

  fun startBatch(context: Context) {
    if (selectedVideos.isEmpty() || profiles.isEmpty()) return

    val videos = selectedVideos.toList()

    batchJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val totalCount = videos.size * profiles.size
        var completedCount = 0
        val results = mutableListOf<BatchTranscodeResult>()

        for (video in videos) {
          ensureActive()
          val playbackUri = copyForPlayback(context, video)

          for ((profileIndex, profile) in profiles.withIndex()) {
            ensureActive()

            val profileName = profileSummary(profile, profileIndex)

            _batchState.value = BatchTranscodingState.InProgress(
              currentVideoName = video.name,
              currentProfileName = profileName,
              currentTranscodePercent = 0,
              completedCount = completedCount,
              totalCount = totalCount
            )

            try {
              val result = transcodeWithProfile(context, video.uri, profile, profileIndex) { percent ->
                _batchState.value = (_batchState.value as? BatchTranscodingState.InProgress)?.copy(
                  currentTranscodePercent = percent
                ) ?: return@transcodeWithProfile
              }
              results.add(
                BatchTranscodeResult(
                  videoName = video.name,
                  originalUri = playbackUri,
                  profileName = profileName,
                  success = true,
                  error = null,
                  originalSize = result.originalSize,
                  outputSize = result.outputSize,
                  outputUri = result.outputUri
                )
              )
              result.originalFile.delete()
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              results.add(
                BatchTranscodeResult(
                  videoName = video.name,
                  originalUri = playbackUri,
                  profileName = profileName,
                  success = false,
                  error = e.message,
                  originalSize = 0,
                  outputSize = 0
                )
              )
            }
            completedCount++
          }
        }

        _batchState.value = BatchTranscodingState.Completed(
          totalTranscoded = results.count { it.success },
          totalFailed = results.count { !it.success },
          results = results
        )
      } catch (e: CancellationException) {
        _batchState.value = BatchTranscodingState.Cancelled
      } catch (e: Exception) {
        _batchState.value = BatchTranscodingState.Failed(e.message ?: "Unknown error")
      }
    }
  }

  private suspend fun transcodeWithProfile(
    context: Context,
    inputUri: Uri,
    profile: TranscodeSettings,
    profileIndex: Int,
    onProgress: (Int) -> Unit
  ): TranscodeTestRepository.TranscodeResult {
    val outputTag = "preset%02d".format(profileIndex + 1)
    return if (profile.quality != null) {
      repository.transcodeWithQuality(
        context = context,
        inputUri = inputUri,
        quality = profile.quality,
        enableFastStart = profile.enableFastStart,
        enableAudioRemux = profile.enableAudioRemux,
        outputTag = outputTag,
        onProgress = onProgress
      )
    } else {
      repository.transcodeWithCustomOptions(
        context = context,
        inputUri = inputUri,
        options = TranscodeTestRepository.CustomTranscodingOptions(
          videoCodec = MediaConverter.VIDEO_CODEC_H264,
          videoResolution = profile.videoResolution,
          videoBitrate = (profile.videoMegaBitrate * MEGABIT).roundToInt(),
          audioBitrate = profile.audioKiloBitrate * KILOBIT,
          enableFastStart = profile.enableFastStart,
          enableAudioRemux = profile.enableAudioRemux
        ),
        outputTag = outputTag,
        onProgress = onProgress
      )
    }
  }

  fun cancelBatch() {
    batchJob?.cancel()
  }

  fun resetBatchState() {
    cancelBatch()
    playbackFiles.forEach { it.delete() }
    playbackFiles.clear()
    _batchState.value = BatchTranscodingState.Idle
  }

  private fun copyForPlayback(context: Context, video: SelectedVideo): Uri {
    val file = File(context.filesDir, "playback-${System.currentTimeMillis()}-${video.name}")
    context.contentResolver.openInputStream(video.uri).use { input ->
      requireNotNull(input) { "Could not open input URI for ${video.name}" }
      file.outputStream().use { input.copyTo(it) }
    }
    playbackFiles.add(file)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  data class SelectedVideo(val uri: Uri, val name: String)

  companion object {
    private const val MEGABIT = 1_000_000
    private const val KILOBIT = 1_000

    fun profileSummary(profile: TranscodeSettings, index: Int): String {
      return if (profile.quality != null) {
        "Profile ${index + 1}: ${profile.quality.name}"
      } else {
        "Profile ${index + 1}: ${profile.videoResolution.shortEdge}p/${"%.1f".format(profile.videoMegaBitrate)}Mbps"
      }
    }
  }
}
