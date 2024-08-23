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
import org.signal.core.util.readLength
import org.thoughtcrime.securesms.video.StreamingTranscoder
import org.thoughtcrime.securesms.video.TranscodingPreset
import org.thoughtcrime.securesms.video.postprocessing.Mp4FaststartPostProcessor
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter.VideoCodec
import org.thoughtcrime.securesms.video.videoconverter.mediadatasource.InputStreamMediaDataSource
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import org.thoughtcrime.video.app.R
import java.io.File
import java.io.FileInputStream
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

    val inputParams = InputParams(inputData)
    val inputFilename = DocumentFile.fromSingleUri(applicationContext, inputParams.inputUri)?.name?.removeFileExtension()
    if (inputFilename == null) {
      Log.w(TAG, "$logPrefix Could not read input file name!")
      return Result.failure()
    }

    val filenameBase = "transcoded-${Instant.now()}-$inputFilename"
    val tempFilename = "$filenameBase$TEMP_FILE_EXTENSION"
    val finalFilename = "$filenameBase$OUTPUT_FILE_EXTENSION"

    setForeground(createForegroundInfo(-1, inputParams.notificationId))
    applicationContext.openFileOutput(tempFilename, Context.MODE_PRIVATE).use { outputStream ->
      if (outputStream == null) {
        Log.w(TAG, "$logPrefix Could not open temp file for I/O!")
        return Result.failure()
      }

      applicationContext.contentResolver.openInputStream(inputParams.inputUri).use { inputStream ->
        applicationContext.openFileOutput(inputFilename, Context.MODE_PRIVATE).use { outputStream ->
          Log.i(TAG, "Started copying input to internal storage.")
          inputStream?.copyTo(outputStream)
          Log.i(TAG, "Finished copying input to internal storage.")
        }
      }
    }

    val datasource = WorkerMediaDataSource(File(applicationContext.filesDir, inputFilename))

    val transcoder = if (inputParams.resolution > 0 && inputParams.videoBitrate > 0) {
      if (inputParams.videoCodec == null) {
        Log.w(TAG, "$logPrefix Video codec was null!")
        return Result.failure()
      }
      Log.d(TAG, "$logPrefix Initializing StreamingTranscoder with custom parameters: CODEC:${inputParams.videoCodec} B:V=${inputParams.videoBitrate}, B:A=${inputParams.audioBitrate}, res=${inputParams.resolution}, audioRemux=${inputParams.audioRemux}")
      StreamingTranscoder.createManuallyForTesting(datasource, null, inputParams.videoCodec, inputParams.videoBitrate, inputParams.audioBitrate, inputParams.resolution, inputParams.audioRemux)
    } else if (inputParams.transcodingPreset != null) {
      StreamingTranscoder(datasource, null, inputParams.transcodingPreset, DEFAULT_FILE_SIZE_LIMIT, inputParams.audioRemux)
    } else {
      throw IllegalArgumentException("Improper input data! No TranscodingPreset defined, or invalid manual parameters!")
    }

    applicationContext.openFileOutput(tempFilename, Context.MODE_PRIVATE).use { outputStream ->
      transcoder.transcode({ percent: Int ->
        if (lastProgress != percent) {
          lastProgress = percent
          Log.v(TAG, "$logPrefix Updating progress percent to $percent%")
          setProgressAsync(Data.Builder().putInt(KEY_PROGRESS, percent).build())
          setForegroundAsync(createForegroundInfo(percent, inputParams.notificationId))
        }
      }, outputStream, { isStopped })
    }

    Log.v(TAG, "$logPrefix Initial transcode completed successfully!")

    val finalFile = createFile(inputParams.outputDirUri, finalFilename) ?: run {
      Log.w(TAG, "$logPrefix Could not create final file for faststart processing!")
      return Result.failure()
    }

    if (!inputParams.postProcessForFastStart) {
      applicationContext.openFileInput(tempFilename).use { tempFileStream ->
        if (tempFileStream == null) {
          Log.w(TAG, "$logPrefix Could not open temp file for I/O!")
          return Result.failure()
        }
        applicationContext.contentResolver.openOutputStream(finalFile.uri, "w").use { finalFileStream ->
          if (finalFileStream == null) {
            Log.w(TAG, "$logPrefix Could not open output file for I/O!")
            return Result.failure()
          }

          tempFileStream.copyTo(finalFileStream)
        }
      }
      Log.v(TAG, "$logPrefix Rename successful.")
    } else {
      val tempFileLength: Long
      applicationContext.openFileInput(tempFilename).use { tempFileStream ->
        if (tempFileStream == null) {
          Log.w(TAG, "$logPrefix Could not open temp file for I/O!")
          return Result.failure()
        }

        tempFileLength = tempFileStream.readLength()
      }

      applicationContext.contentResolver.openOutputStream(finalFile.uri, "w").use { finalFileStream ->
        if (finalFileStream == null) {
          Log.w(TAG, "$logPrefix Could not open output file for I/O!")
          return Result.failure()
        }

        val inputStreamFactory = { applicationContext.openFileInput(tempFilename) ?: throw IOException("Could not open temp file for reading!") }
        val bytesCopied = Mp4FaststartPostProcessor(inputStreamFactory).processAndWriteTo(finalFileStream)

        if (bytesCopied != tempFileLength) {
          Log.w(TAG, "$logPrefix Postprocessing failed! Original transcoded filesize ($tempFileLength) did not match postprocessed filesize ($bytesCopied)")
          return Result.failure()
        }

        Log.v(TAG, "$logPrefix Faststart postprocess successful.")
      }
      val tempFile = File(applicationContext.filesDir, tempFilename)
      if (!tempFile.delete()) {
        Log.w(TAG, "$logPrefix Failed to delete temp file after processing!")
        return Result.failure()
      }
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

  private fun String.removeFileExtension(): String {
    val lastDot = this.lastIndexOf('.')
    return if (lastDot != -1) {
      this.substring(0, lastDot)
    } else {
      this
    }
  }

  private class WorkerMediaDataSource(private val file: File) : InputStreamMediaDataSource() {

    private val size = file.length()

    private var inputStream: InputStream? = null

    override fun close() {
      inputStream?.close()
    }

    override fun getSize(): Long {
      return size
    }

    override fun createInputStream(position: Long): InputStream {
      inputStream?.close()
      val openedInputStream = FileInputStream(file)
      openedInputStream.skip(position)
      inputStream = openedInputStream
      return openedInputStream
    }
  }

  private data class InputParams(private val inputData: Data) {
    val notificationId: Int = inputData.getInt(KEY_NOTIFICATION_ID, -1)
    val inputUri: Uri = Uri.parse(inputData.getString(KEY_INPUT_URI))
    val outputDirUri: Uri = Uri.parse(inputData.getString(KEY_OUTPUT_URI))
    val postProcessForFastStart: Boolean = inputData.getBoolean(KEY_ENABLE_FASTSTART, true)
    val transcodingPreset: TranscodingPreset? = inputData.getString(KEY_TRANSCODING_PRESET_NAME)?.let { TranscodingPreset.valueOf(it) }

    @VideoCodec val videoCodec: String? = inputData.getString(KEY_VIDEO_CODEC)
    val resolution: Int = inputData.getInt(KEY_SHORT_EDGE, -1)
    val videoBitrate: Int = inputData.getInt(KEY_VIDEO_BIT_RATE, -1)
    val audioBitrate: Int = inputData.getInt(KEY_AUDIO_BIT_RATE, -1)
    val audioRemux: Boolean = inputData.getBoolean(KEY_ENABLE_AUDIO_REMUX, true)
  }

  companion object {
    private const val TAG = "TranscodeWorker"
    private const val OUTPUT_FILE_EXTENSION = ".mp4"
    const val TEMP_FILE_EXTENSION = ".tmp"
    private const val DEFAULT_FILE_SIZE_LIMIT: Long = 100 * 1024 * 1024
    const val KEY_INPUT_URI = "input_uri"
    const val KEY_OUTPUT_URI = "output_uri"
    const val KEY_TRANSCODING_PRESET_NAME = "transcoding_quality_preset"
    const val KEY_PROGRESS = "progress"
    const val KEY_VIDEO_CODEC = "video_codec"
    const val KEY_LONG_EDGE = "resolution_long_edge"
    const val KEY_SHORT_EDGE = "resolution_short_edge"
    const val KEY_VIDEO_BIT_RATE = "video_bit_rate"
    const val KEY_AUDIO_BIT_RATE = "audio_bit_rate"
    const val KEY_ENABLE_AUDIO_REMUX = "audio_remux"
    const val KEY_ENABLE_FASTSTART = "video_enable_faststart"
    const val KEY_NOTIFICATION_ID = "notification_id"
  }
}
