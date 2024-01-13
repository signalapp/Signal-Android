/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlin.math.roundToInt

/**
 * ViewModel for the transcoding screen of the video sample app. See [TranscodeTestActivity].
 */
class TranscodeTestViewModel : ViewModel() {
  private lateinit var repository: TranscodeTestRepository
  private var backPressedRunnable = {}
  private var transcodingJobs: Map<UUID, Uri> = emptyMap()

  var outputDirectory: Uri? by mutableStateOf(null)
    private set
  var selectedVideos: List<Uri> by mutableStateOf(emptyList())
  var videoMegaBitrate = DEFAULT_VIDEO_MEGABITRATE
  var videoResolution = VideoResolution.HD
  var useAutoTranscodingSettings = true
  var enableFastStart = true
  var forceSequentialQueueProcessing = false

  fun initialize(context: Context) {
    repository = TranscodeTestRepository(context)
    backPressedRunnable = { Toast.makeText(context, "Cancelling all transcoding jobs!", Toast.LENGTH_LONG).show() }
  }

  fun transcode() {
    val output = outputDirectory ?: throw IllegalStateException("No output directory selected!")
    if (useAutoTranscodingSettings) {
      transcodingJobs = repository.transcode(selectedVideos, output, forceSequentialQueueProcessing, null)
    } else {
      transcodingJobs = repository.transcode(selectedVideos, output, forceSequentialQueueProcessing, TranscodeTestRepository.CustomTranscodingOptions(videoResolution, (videoMegaBitrate * MEGABIT).roundToInt(), enableFastStart))
    }
  }

  fun getTranscodingJobsAsState(): Flow<MutableList<WorkInfo>> {
    return repository.getTranscodingJobsAsFlow(transcodingJobs.keys.toList())
  }

  fun setOutputDirectoryAndCleanFailedTranscodes(context: Context, folderUri: Uri) {
    outputDirectory = folderUri
    repository.cleanFailedTranscodes(context, folderUri)
  }

  fun getUriFromJobId(jobId: UUID): Uri? {
    return transcodingJobs[jobId]
  }

  fun reset() {
    cancelAllTranscodes()
    resetOutputDirectory()
    selectedVideos = emptyList()
  }

  fun cancelAllTranscodes() {
    repository.cancelAllTranscodes()
    transcodingJobs = emptyMap()
  }

  fun resetOutputDirectory() {
    outputDirectory = null
  }

  companion object {
    private const val MEGABIT = 1000000
  }
}
