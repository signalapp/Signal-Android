/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.signal.core.util.readToList
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Repository to perform various transcoding functions.
 */
class TranscodeTestRepository(context: Context) {
  private val workManager = WorkManager.getInstance(context)
  private val usedNotificationIds = emptySet<Int>()

  fun transcode(selectedVideos: List<Uri>, outputDirectory: Uri, forceSequentialProcessing: Boolean, customTranscodingOptions: CustomTranscodingOptions?): Map<UUID, Uri> {
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

      if (customTranscodingOptions != null) {
        inputData.putInt(TranscodeWorker.KEY_LONG_EDGE, customTranscodingOptions.videoResolution.longEdge)
        inputData.putInt(TranscodeWorker.KEY_SHORT_EDGE, customTranscodingOptions.videoResolution.shortEdge)
        inputData.putInt(TranscodeWorker.KEY_BIT_RATE, customTranscodingOptions.bitrate)
        inputData.putBoolean(TranscodeWorker.KEY_ENABLE_FASTSTART, customTranscodingOptions.enableFastStart)
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

  fun cleanFailedTranscodes(context: Context, folderUri: Uri) {
    val docs = queryChildDocuments(context, folderUri)
    docs.filter { it.documentId.endsWith(".tmp") }.forEach {
      val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, it.documentId)
      DocumentsContract.deleteDocument(context.contentResolver, fileUri)
    }
  }

  private fun queryChildDocuments(context: Context, folderUri: Uri): List<FileMetadata> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
      folderUri,
      DocumentsContract.getTreeDocumentId(folderUri)
    )

    context.contentResolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE),
      null,
      null,
      null
    ).use { cursor ->
      if (cursor == null) {
        return emptyList()
      }
      return cursor.readToList {
        FileMetadata(
          documentId = it.getString(0),
          label = it.getString(1),
          size = it.getLong(2)
        )
      }
    }
  }

  private data class FileMetadata(val documentId: String, val label: String, val size: Long)

  data class CustomTranscodingOptions(val videoResolution: VideoResolution, val bitrate: Int, val enableFastStart: Boolean)

  companion object {
    private const val TAG = "TranscodingTestRepository"
    const val TRANSCODING_WORK_TAG = "transcoding"
  }
}
