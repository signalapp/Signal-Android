/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.thoughtcrime.securesms.video.TranscodingPreset
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Repository to perform various transcoding functions.
 */
class TranscodeTestRepository(context: Context) {
  private val workManager = WorkManager.getInstance(context)
  private val usedNotificationIds = emptySet<Int>()

  private fun transcode(selectedVideos: List<Uri>, outputDirectory: Uri, forceSequentialProcessing: Boolean, transcodingPreset: TranscodingPreset? = null, customTranscodingOptions: CustomTranscodingOptions? = null): Map<UUID, Uri> {
    if (customTranscodingOptions == null && transcodingPreset == null) {
      throw IllegalArgumentException("Must define either custom options or transcoding preset!")
    } else if (customTranscodingOptions != null && transcodingPreset != null) {
      throw IllegalArgumentException("Cannot define both custom options and transcoding preset!")
    }

    if (selectedVideos.isEmpty()) {
      return emptyMap()
    }

    val urisAndRequests = selectedVideos.map {
      var notificationId = Random.nextInt().absoluteValue
      while (usedNotificationIds.contains(notificationId)) {
        notificationId = Random.nextInt().absoluteValue
      }
      val inputData = Data.Builder()
        .putString(TranscodeWorker.KEY_INPUT_URI, it.toString())
        .putString(TranscodeWorker.KEY_OUTPUT_URI, outputDirectory.toString())
        .putInt(TranscodeWorker.KEY_NOTIFICATION_ID, notificationId)

      if (transcodingPreset != null) {
        inputData.putString(TranscodeWorker.KEY_TRANSCODING_PRESET_NAME, transcodingPreset.name)
      } else if (customTranscodingOptions != null) {
        inputData.putString(TranscodeWorker.KEY_VIDEO_CODEC, customTranscodingOptions.videoCodec)
        inputData.putInt(TranscodeWorker.KEY_LONG_EDGE, customTranscodingOptions.videoResolution.longEdge)
        inputData.putInt(TranscodeWorker.KEY_SHORT_EDGE, customTranscodingOptions.videoResolution.shortEdge)
        inputData.putInt(TranscodeWorker.KEY_VIDEO_BIT_RATE, customTranscodingOptions.videoBitrate)
        inputData.putInt(TranscodeWorker.KEY_AUDIO_BIT_RATE, customTranscodingOptions.audioBitrate)
        inputData.putBoolean(TranscodeWorker.KEY_ENABLE_FASTSTART, customTranscodingOptions.enableFastStart)
        inputData.putBoolean(TranscodeWorker.KEY_ENABLE_AUDIO_REMUX, customTranscodingOptions.enableAudioRemux)
      }

      val transcodeRequest = OneTimeWorkRequestBuilder<TranscodeWorker>()
        .setInputData(inputData.build())
        .addTag(TRANSCODING_WORK_TAG)
        .build()
      it to transcodeRequest
    }
    val idsToUris = urisAndRequests.associateBy({ it.second.id }, { it.first })
    val requests = urisAndRequests.map { it.second }
    if (forceSequentialProcessing) {
      var continuation = workManager.beginWith(requests.first())
      for (request in requests.drop(1)) {
        continuation = continuation.then(request)
      }
      continuation.enqueue()
    } else {
      workManager.enqueue(requests)
    }
    return idsToUris
  }

  fun transcodeWithCustomOptions(selectedVideos: List<Uri>, outputDirectory: Uri, forceSequentialProcessing: Boolean, customTranscodingOptions: CustomTranscodingOptions?): Map<UUID, Uri> {
    return transcode(selectedVideos, outputDirectory, forceSequentialProcessing, customTranscodingOptions = customTranscodingOptions)
  }

  fun transcodeWithPresetOptions(selectedVideos: List<Uri>, outputDirectory: Uri, forceSequentialProcessing: Boolean, transcodingPreset: TranscodingPreset): Map<UUID, Uri> {
    return transcode(selectedVideos, outputDirectory, forceSequentialProcessing, transcodingPreset)
  }

  fun getTranscodingJobsAsFlow(jobIds: List<UUID>): Flow<MutableList<WorkInfo>> {
    if (jobIds.isEmpty()) {
      return emptyFlow()
    }
    return workManager.getWorkInfosFlow(WorkQuery.fromIds(jobIds))
  }

  fun cancelAllTranscodes() {
    workManager.cancelAllWorkByTag(TRANSCODING_WORK_TAG)
    workManager.pruneWork()
  }

  fun cleanPrivateStorage(context: Context) {
    context.filesDir.listFiles()?.forEach {
      it.delete()
    }
  }

  data class CustomTranscodingOptions(val videoCodec: String, val videoResolution: VideoResolution, val videoBitrate: Int, val audioBitrate: Int, val enableFastStart: Boolean, val enableAudioRemux: Boolean)

  companion object {
    private const val TAG = "TranscodingTestRepository"
    const val TRANSCODING_WORK_TAG = "transcoding"
  }
}
