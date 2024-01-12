/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.signal.core.util.getLength
import org.thoughtcrime.securesms.video.StreamingTranscoder
import org.thoughtcrime.securesms.video.videoconverter.VideoConstants
import org.thoughtcrime.securesms.video.videoconverter.mediadatasource.InputStreamMediaDataSource
import org.thoughtcrime.video.app.R
import java.io.FileOutputStream
import java.io.InputStream
import java.time.Instant

class TranscodeWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
  @UnstableApi
  override suspend fun doWork(): Result {
    val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, -1)
    if (notificationId < 0) {
      Log.w(TAG, "Notification ID was null!")
      return Result.failure()
    }

    val inputUri = inputData.getString(KEY_INPUT_URI)
    if (inputUri == null) {
      Log.w(TAG, "Input URI was null!")
      return Result.failure()
    }

    val outputDirUri = inputData.getString(KEY_OUTPUT_URI)
    if (outputDirUri == null) {
      Log.w(TAG, "Output URI was null!")
      return Result.failure()
    }

    val input = DocumentFile.fromSingleUri(ctx, Uri.parse(inputUri))?.name

    if (input == null) {
      Log.w(TAG, "Could not read input file name!")
      return Result.failure()
    }

    val outputFileUri = createFile(Uri.parse(outputDirUri), "transcoded-${Instant.now()}-$input$OUTPUT_FILE_EXTENSION")

    if (outputFileUri == null) {
      Log.w(TAG, "Could not create output file!")
      return Result.failure()
    }

    val datasource = WorkerMediaDataSource(ctx, Uri.parse(inputUri))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      Log.w(TAG, "Transcoder is only supported on API 26+!")
      return Result.failure()
    }
    val transcoder = StreamingTranscoder(datasource, null, 50 * 1024 * 1024) // TODO: set options
    setForeground(createForegroundInfo(-1, notificationId))
    ctx.contentResolver.openFileDescriptor(outputFileUri, "w").use { it: ParcelFileDescriptor? ->
      if (it == null) {
        Log.w(TAG, "Could not open output file for writing!")
        return Result.failure()
      }
      transcoder.transcode(
        { percent: Int ->
          setProgressAsync(Data.Builder().putInt(KEY_PROGRESS, percent).build())
          setForegroundAsync(createForegroundInfo(percent, notificationId))
        },
        FileOutputStream(it.fileDescriptor),
        { isStopped }
      )
      return Result.success()
    }
  }

  private fun createForegroundInfo(progress: Int, notificationId: Int): ForegroundInfo {
    val id = applicationContext.getString(R.string.notification_channel_id)
    val title = applicationContext.getString(R.string.notification_title)
    val cancel = applicationContext.getString(R.string.cancel_transcode)
    val intent = WorkManager.getInstance(applicationContext)
      .createCancelPendingIntent(getId())

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = applicationContext.getString(R.string.channel_name)
      val descriptionText = applicationContext.getString(R.string.channel_description)
      val importance = NotificationManager.IMPORTANCE_LOW
      val mChannel = NotificationChannel(id, name, importance)
      mChannel.description = descriptionText
      val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(mChannel)
    }

    val notification = NotificationCompat.Builder(applicationContext, id)
      .setContentTitle(title)
      .setTicker(title)
      .setProgress(100, progress, progress >= 0)
      .setSmallIcon(R.drawable.ic_work_notification)
      .setOngoing(true)
      .addAction(android.R.drawable.ic_delete, cancel, intent)
      .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(notificationId, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(notificationId, notification)
    }
  }

  private fun createFile(treeUri: Uri, filename: String): Uri? {
    return DocumentFile.fromTreeUri(ctx, treeUri)?.createFile(VideoConstants.VIDEO_MIME_TYPE, filename)?.uri
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
    const val KEY_INPUT_URI = "input_uri"
    const val KEY_OUTPUT_URI = "output_uri"
    const val KEY_PROGRESS = "progress"
    const val KEY_NOTIFICATION_ID = "notification_id"
  }
}
