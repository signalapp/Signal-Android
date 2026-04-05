package org.thoughtcrime.securesms.jobs

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.signal.core.util.PendingIntentFlags.immutable
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.LocalExportProgress
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.keyvalue.protos.LocalBackupCreationProgress
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.service.GenericForegroundService
import org.thoughtcrime.securesms.service.NotificationController
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalPlaintextArchiveJob internal constructor(
  private val destinationUri: String,
  private val includeMedia: Boolean,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY: String = "LocalPlaintextArchiveJob"

    private val TAG = Log.tag(LocalPlaintextArchiveJob::class.java)

    private const val KEY_DESTINATION_URI = "destination_uri"
    private const val KEY_INCLUDE_MEDIA = "include_media"
  }

  private var exportDir: DocumentFile? = null

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_DESTINATION_URI, destinationUri)
      .putBoolean(KEY_INCLUDE_MEDIA, includeMedia)
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    Log.i(TAG, "Executing plaintext archive job...")

    val contentIntent = PendingIntent.getActivity(
      context,
      0,
      AppSettingsActivity.chats(context).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      immutable()
    )

    var notification: NotificationController? = null
    try {
      notification = GenericForegroundService.startForegroundTask(
        context,
        context.getString(R.string.LocalBackupJob_creating_signal_backup),
        NotificationChannels.getInstance().BACKUPS,
        R.drawable.ic_signal_backup,
        contentIntent
      )
    } catch (e: UnableToStartException) {
      Log.w(TAG, "Unable to start foreground service, continuing without service")
    }

    try {
      notification?.setIndeterminateProgress()
      setProgress(LocalBackupCreationProgress(exporting = LocalBackupCreationProgress.Exporting(phase = LocalBackupCreationProgress.ExportPhase.INITIALIZING)), notification)

      val stopwatch = Stopwatch("plaintext-archive-export")

      val root = DocumentFile.fromTreeUri(context, Uri.parse(destinationUri))
      if (root == null || !root.canWrite()) {
        Log.w(TAG, "Cannot write to destination directory.")
        return Result.failure()
      }

      val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
      val fileName = "signal-export-$timestamp"

      exportDir = root.createDirectory(fileName)
      val exportDir = this.exportDir ?: run {
        Log.w(TAG, "Unable to create export directory")
        return Result.failure()
      }

      stopwatch.split("create-dir")

      try {
        SignalDatabase.attachmentMetadata.insertNewKeysForExistingAttachments()

        val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        progressScope.launch {
          LocalExportProgress.plaintextProgress.collect { progress ->
            updateNotification(progress, notification)
          }
        }

        try {
          val result = LocalArchiver.exportPlaintext(exportDir, context.contentResolver, includeMedia, stopwatch, cancellationSignal = { isCanceled })
          Log.i(TAG, "Plaintext archive finished with result: $result")
          if (isCanceled) {
            exportDir.delete()
            setProgress(LocalBackupCreationProgress(canceled = LocalBackupCreationProgress.Canceled()), notification)
            return Result.failure()
          } else if (result !is org.signal.core.util.Result.Success) {
            exportDir.delete()
            setProgress(LocalBackupCreationProgress(failed = LocalBackupCreationProgress.Failed()), notification)
            return Result.failure()
          }
        } finally {
          progressScope.cancel()
        }

        stopwatch.split("archive-create")
        setProgress(LocalBackupCreationProgress(succeeded = LocalBackupCreationProgress.Succeeded()), notification)
      } catch (e: IOException) {
        Log.w(TAG, "Error during plaintext archive!", e)
        setProgress(LocalBackupCreationProgress(failed = LocalBackupCreationProgress.Failed()), notification)
        exportDir.delete()
        throw e
      }

      stopwatch.stop(TAG)
    } finally {
      notification?.close()
    }

    return Result.success()
  }

  override fun onFailure() {
    exportDir?.delete()
    val current = LocalExportProgress.plaintextProgress.value
    if (current.canceled == null && current.failed == null) {
      LocalExportProgress.setPlaintextProgress(LocalBackupCreationProgress(failed = LocalBackupCreationProgress.Failed()))
    }
  }

  private fun setProgress(progress: LocalBackupCreationProgress, notification: NotificationController?) {
    LocalExportProgress.setPlaintextProgress(progress)
    updateNotification(progress, notification)
  }

  private var previousPhase: NotificationPhase? = null

  private fun updateNotification(progress: LocalBackupCreationProgress, notification: NotificationController?) {
    if (notification == null) return

    val exporting = progress.exporting
    val transferring = progress.transferring

    when {
      exporting != null -> {
        val phase = NotificationPhase.Export(exporting.phase)
        val title = when (exporting.phase) {
          LocalBackupCreationProgress.ExportPhase.MESSAGE -> {
            if (exporting.frameTotalCount > 0) {
              context.getString(
                R.string.BackupCreationProgressRow__processing_messages_s_of_s_d,
                "%,d".format(exporting.frameExportCount),
                "%,d".format(exporting.frameTotalCount),
                (exporting.frameExportCount * 100 / exporting.frameTotalCount).toInt()
              )
            } else {
              context.getString(R.string.BackupCreationProgressRow__processing_messages)
            }
          }
          LocalBackupCreationProgress.ExportPhase.FINALIZING -> context.getString(R.string.BackupCreationProgressRow__finalizing)
          LocalBackupCreationProgress.ExportPhase.NONE -> context.getString(R.string.BackupCreationProgressRow__processing_backup)
          else -> context.getString(R.string.BackupCreationProgressRow__preparing_backup)
        }
        if (previousPhase != phase || exporting.phase == LocalBackupCreationProgress.ExportPhase.MESSAGE) {
          notification.replaceTitle(title)
          previousPhase = phase
        }
        if (exporting.frameTotalCount == 0L) {
          notification.setIndeterminateProgress()
        } else {
          notification.setProgress(exporting.frameTotalCount, exporting.frameExportCount)
        }
      }

      transferring != null -> {
        if (previousPhase !is NotificationPhase.Transfer) {
          notification.replaceTitle(AppDependencies.application.getString(R.string.LocalArchiveJob__exporting_media))
          previousPhase = NotificationPhase.Transfer
        }
        if (transferring.total == 0L) {
          notification.setIndeterminateProgress()
        } else {
          notification.setProgress(transferring.total, transferring.completed)
        }
      }

      else -> {
        notification.setIndeterminateProgress()
      }
    }
  }

  private sealed interface NotificationPhase {
    data class Export(val phase: LocalBackupCreationProgress.ExportPhase) : NotificationPhase
    data object Transfer : NotificationPhase
  }

  class Factory : Job.Factory<LocalPlaintextArchiveJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): LocalPlaintextArchiveJob {
      val data = JsonJobData.deserialize(serializedData)
      return LocalPlaintextArchiveJob(
        destinationUri = data.getString(KEY_DESTINATION_URI),
        includeMedia = data.getBoolean(KEY_INCLUDE_MEDIA),
        parameters = parameters
      )
    }
  }
}
