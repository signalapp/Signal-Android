/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.signal.core.util.getLength
import org.signal.core.util.readLength
import org.thoughtcrime.securesms.video.StreamingTranscoder
import org.thoughtcrime.securesms.video.TranscodingPreset
import org.thoughtcrime.securesms.video.postprocessing.Mp4FaststartPostProcessor
import org.thoughtcrime.securesms.video.videoconverter.mediadatasource.InputStreamMediaDataSource
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import org.thoughtcrime.video.app.R
import java.io.IOException
import java.io.InputStream
import java.time.Instant

/**
 * A WorkManager worker to transcode videos in the background. This utilizes [StreamingTranscoder].
 */
class TranscodeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
  private var lastProgress = 0

  @UnstableApi
  override suspend fun doWork(): Result {
    val logPrefix = "[Job ${id.toString().takeLast(4)}]"

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      Log.w(TAG, "$logPrefix Transcoder is only supported on API 26+!")
      return Result.failure()
    }

    val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, -1)
    if (notificationId < 0) {
      Log.w(TAG, "$logPrefix Notification ID was null!")
      return Result.failure()
    }

    val inputUri = inputData.getString(KEY_INPUT_URI)
    if (inputUri == null) {
      Log.w(TAG, "$logPrefix Input URI was null!")
      return Result.failure()
    }

    val outputDirUri = inputData.getString(KEY_OUTPUT_URI)
    if (outputDirUri == null) {
      Log.w(TAG, "$logPrefix Output URI was null!")
      return Result.failure()
    }

    val postProcessForFastStart = inputData.getBoolean(KEY_ENABLE_FASTSTART, true)
    val transcodingPreset = inputData.getString(KEY_TRANSCODING_PRESET_NAME)
    val resolution = inputData.getInt(KEY_SHORT_EDGE, -1)
    val videoBitrate = inputData.getInt(KEY_VIDEO_BIT_RATE, -1)
    val audioBitrate = inputData.getInt(KEY_AUDIO_BIT_RATE, -1)
    val audioRemux = inputData.getBoolean(KEY_ENABLE_AUDIO_REMUX, true)

    val input = DocumentFile.fromSingleUri(applicationContext, Uri.parse(inputUri))?.name
    if (input == null) {
      Log.w(TAG, "$logPrefix Could not read input file name!")
      return Result.failure()
    }

    val filenameBase = "transcoded-${Instant.now()}-$input"
    val tempFilename = "$filenameBase$TEMP_FILE_EXTENSION"
    val finalFilename = "$filenameBase$OUTPUT_FILE_EXTENSION"

    val tempFile = createFile(Uri.parse(outputDirUri), tempFilename)
    if (tempFile == null) {
      Log.w(TAG, "$logPrefix Could not create temp file!")
      return Result.failure()
    }

    val datasource = WorkerMediaDataSource(applicationContext, Uri.parse(inputUri))

    val transcoder = if (resolution > 0 && videoBitrate > 0) {
      Log.d(TAG, "$logPrefix Initializing StreamingTranscoder with custom parameters: B:V=$videoBitrate, B:A=$audioBitrate, res=$resolution, audioRemux=$audioRemux")
      StreamingTranscoder.createManuallyForTesting(datasource, null, videoBitrate, audioBitrate, resolution, audioRemux)
    } else if (transcodingPreset != null) {
      StreamingTranscoder(datasource, null, TranscodingPreset.valueOf(transcodingPreset), DEFAULT_FILE_SIZE_LIMIT, audioRemux)
    } else {
      throw IllegalArgumentException("Improper input data! No TranscodingPreset defined, or invalid manual parameters!")
    }

    setForeground(createForegroundInfo(-1, notificationId))
    applicationContext.contentResolver.openOutputStream(tempFile.uri).use { outputStream ->
      if (outputStream == null) {
        Log.w(TAG, "$logPrefix Could not open temp file for I/O!")
        return Result.failure()
      }
      transcoder.transcode({ percent: Int ->
        if (lastProgress != percent) {
          lastProgress = percent
          Log.v(TAG, "$logPrefix Updating progress percent to $percent%")
          setProgressAsync(Data.Builder().putInt(KEY_PROGRESS, percent).build())
          setForegroundAsync(createForegroundInfo(percent, notificationId))
        }
      }, outputStream, { isStopped })
    }
    Log.v(TAG, "$logPrefix Initial transcode completed successfully!")
    if (!postProcessForFastStart) {
      tempFile.renameTo(finalFilename)
      Log.v(TAG, "$logPrefix Rename successful.")
    } else {
      val tempFileLength: Long
      applicationContext.contentResolver.openInputStream(tempFile.uri).use { tempFileStream ->
        if (tempFileStream == null) {
          Log.w(TAG, "$logPrefix Could not open temp file for I/O!")
          return Result.failure()
        }

        tempFileLength = tempFileStream.readLength()
      }
      val finalFile = createFile(Uri.parse(outputDirUri), finalFilename) ?: run {
        Log.w(TAG, "$logPrefix Could not create final file for faststart processing!")
        return Result.failure()
      }
      applicationContext.contentResolver.openOutputStream(finalFile.uri, "w").use { finalFileStream ->
        if (finalFileStream == null) {
          Log.w(TAG, "$logPrefix Could not open output file for I/O!")
          return Result.failure()
        }

        val inputStreamFactory = { applicationContext.contentResolver.openInputStream(tempFile.uri) ?: throw IOException("Could not open temp file for reading!") }
        val bytesCopied = Mp4FaststartPostProcessor(inputStreamFactory).processAndWriteTo(finalFileStream)

        if (bytesCopied != tempFileLength) {
          Log.w(TAG, "$logPrefix Postprocessing failed! Original transcoded filesize ($tempFileLength) did not match postprocessed filesize ($bytesCopied)")
          return Result.failure()
        }

        if (!tempFile.delete()) {
          Log.w(TAG, "$logPrefix Failed to delete temp file after processing!")
          return Result.failure()
        }
      }
      Log.v(TAG, "$logPrefix Faststart postprocess successful.")
    }
    Log.v(TAG, "$logPrefix Overall transcode job successful.")
    return Result.success()
  }

  private fun createForegroundInfo(progress: Int, notificationId: Int): ForegroundInfo {
    val id = applicationContext.getString(R.string.notification_channel_id)
    val title = applicationContext.getString(R.string.notification_title)
    val cancel = applicationContext.getString(R.string.cancel_transcode)
    val intent = WorkManager.getInstance(applicationContext)
      .createCancelPendingIntent(getId())
    val transcodeActivityIntent = Intent(applicationContext, TranscodeTestActivity::class.java)
    val pendingIntent: PendingIntent? = TaskStackBuilder.create(applicationContext).run {
      addNextIntentWithParentStack(transcodeActivityIntent)
      getPendingIntent(
        0,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    }
    val notification = NotificationCompat.Builder(applicationContext, id)
      .setContentTitle(title)
      .setTicker(title)
      .setProgress(100, progress, progress <= 0)
      .setSmallIcon(R.drawable.ic_work_notification)
      .setOngoing(true)
      .setContentIntent(pendingIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .addAction(android.R.drawable.ic_delete, cancel, intent)
      .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(notificationId, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(notificationId, notification)
    }
  }

  private fun createFile(treeUri: Uri, filename: String): DocumentFile? {
    return DocumentFile.fromTreeUri(applicationContext, treeUri)?.createFile(VideoConstants.VIDEO_MIME_TYPE, filename)
  }

  private class WorkerMediaDataSource(context: Context, private val uri: Uri) : InputStreamMediaDataSource() {

    private val contentResolver = context.contentResolver
    private val size = contentResolver.getLength(uri) ?: throw IllegalStateException()

    private var inputStream: InputStream? = null

    override fun close() {
      inputStream?.close()
    }

    override fun getSize(): Long {
      return size
    }

    override fun createInputStream(position: Long): InputStream {
      inputStream?.close()
      val openedInputStream = contentResolver.openInputStream(uri) ?: throw IllegalStateException()
      openedInputStream.skip(position)
      inputStream = openedInputStream
      return openedInputStream
    }
  }

  companion object {
    private const val TAG = "TranscodeWorker"
    private const val OUTPUT_FILE_EXTENSION = ".mp4"
    private const val TEMP_FILE_EXTENSION = ".tmp"
    private const val DEFAULT_FILE_SIZE_LIMIT: Long = 100 * 1024 * 1024
    const val KEY_INPUT_URI = "input_uri"
    const val KEY_OUTPUT_URI = "output_uri"
    const val KEY_TRANSCODING_PRESET_NAME = "transcoding_quality_preset"
    const val KEY_PROGRESS = "progress"
    const val KEY_LONG_EDGE = "resolution_long_edge"
    const val KEY_SHORT_EDGE = "resolution_short_edge"
    const val KEY_VIDEO_BIT_RATE = "video_bit_rate"
    const val KEY_AUDIO_BIT_RATE = "audio_bit_rate"
    const val KEY_ENABLE_AUDIO_REMUX = "audio_remux"
    const val KEY_ENABLE_FASTSTART = "video_enable_faststart"
    const val KEY_NOTIFICATION_ID = "notification_id"
  }
}
