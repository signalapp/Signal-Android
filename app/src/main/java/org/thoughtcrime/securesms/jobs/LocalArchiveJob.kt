package org.thoughtcrime.securesms.jobs

import android.net.Uri
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.BackupFileIOError
import org.thoughtcrime.securesms.backup.FullBackupExporter.BackupCanceledException
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.LocalBackupCreationProgress
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.service.GenericForegroundService
import org.thoughtcrime.securesms.service.NotificationController
import java.io.IOException

/**
 * Local backup job for installs using new backupv2 folder format.
 *
 * @see LocalBackupJob.enqueue
 */
class LocalArchiveJob internal constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    const val KEY: String = "LocalArchiveJob"

    private val TAG = Log.tag(LocalArchiveJob::class.java)
  }

  override fun serialize(): ByteArray? {
    return null
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    Log.i(TAG, "Executing backup job...")

    BackupFileIOError.clearNotification(context)

    var notification: NotificationController? = null
    try {
      notification = GenericForegroundService.startForegroundTask(
        context,
        context.getString(R.string.LocalBackupJob_creating_signal_backup),
        NotificationChannels.getInstance().BACKUPS,
        R.drawable.ic_signal_backup
      )
    } catch (e: UnableToStartException) {
      Log.w(TAG, "Unable to start foreground backup service, continuing without service")
    }

    try {
      notification?.setIndeterminateProgress()
      setProgress(LocalBackupCreationProgress(exporting = LocalBackupCreationProgress.Exporting(phase = LocalBackupCreationProgress.ExportPhase.INITIALIZING)), notification)

      val stopwatch = Stopwatch("archive-export")

      val backupDirectoryUri = SignalStore.backup.newLocalBackupsDirectory?.let { Uri.parse(it) }
      if (backupDirectoryUri == null || backupDirectoryUri.path == null) {
        throw IOException("Backup Directory has not been selected!")
      }
      val archiveFileSystem = ArchiveFileSystem.fromUri(context, backupDirectoryUri)

      if (archiveFileSystem == null) {
        BackupFileIOError.ACCESS_ERROR.postNotification(context)
        Log.w(TAG, "Cannot write to backup directory location.")
        return Result.failure()
      }
      stopwatch.split("create-fs")

      archiveFileSystem.deleteOldTemporaryBackups()
      stopwatch.split("delete-old")

      val snapshotFileSystem: SnapshotFileSystem = archiveFileSystem.createSnapshot() ?: return Result.failure()
      stopwatch.split("create-snapshot")

      try {
        SignalDatabase.attachmentMetadata.insertNewKeysForExistingAttachments()

        try {
          val result = LocalArchiver.export(snapshotFileSystem, archiveFileSystem.filesFileSystem, stopwatch, cancellationSignal = { isCanceled })
          Log.i(TAG, "Archive finished with result: $result")
          if (result !is org.signal.core.util.Result.Success) {
            return Result.failure()
          }
        } catch (e: Exception) {
          Log.w(TAG, "Unable to create local archive", e)
          return Result.failure()
        }

        stopwatch.split("archive-create")

        snapshotFileSystem.finalize()
        stopwatch.split("archive-finalize")

        setProgress(LocalBackupCreationProgress(exporting = LocalBackupCreationProgress.Exporting(phase = LocalBackupCreationProgress.ExportPhase.FINALIZING)), notification)
      } catch (e: BackupCanceledException) {
        setProgress(LocalBackupCreationProgress(idle = LocalBackupCreationProgress.Idle()), notification)
        Log.w(TAG, "Archive cancelled")
        throw e
      } catch (e: IOException) {
        Log.w(TAG, "Error during archive!", e)
        setProgress(LocalBackupCreationProgress(idle = LocalBackupCreationProgress.Idle()), notification)
        BackupFileIOError.postNotificationForException(context, e)
        throw e
      } finally {
        val cleanUpWasRequired = archiveFileSystem.cleanupSnapshot(snapshotFileSystem)
        if (cleanUpWasRequired) {
          Log.w(TAG, "Archive failed. Snapshot temp folder needed to be deleted")
        }
      }
      stopwatch.split("new-archive-done")

      archiveFileSystem.deleteOldBackups()
      stopwatch.split("delete-old")

      archiveFileSystem.deleteUnusedFiles { completed, total ->
        setProgress(LocalBackupCreationProgress(exporting = LocalBackupCreationProgress.Exporting(phase = LocalBackupCreationProgress.ExportPhase.FINALIZING, frameExportCount = completed.toLong(), frameTotalCount = total.toLong())), notification)
      }
      stopwatch.split("delete-unused")

      stopwatch.stop(TAG)

      setProgress(LocalBackupCreationProgress(idle = LocalBackupCreationProgress.Idle()), notification)
      SignalStore.backup.newLocalBackupsLastBackupTime = System.currentTimeMillis()
    } finally {
      notification?.close()
    }

    return Result.success()
  }

  override fun onFailure() {
    SignalStore.backup.newLocalBackupProgress = LocalBackupCreationProgress(idle = LocalBackupCreationProgress.Idle())
  }

  private fun setProgress(progress: LocalBackupCreationProgress, notification: NotificationController?) {
    SignalStore.backup.newLocalBackupProgress = progress
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
        if (previousPhase != phase) {
          notification.replaceTitle(exporting.phase.toString())
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

  class Factory : Job.Factory<LocalArchiveJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): LocalArchiveJob {
      return LocalArchiveJob(parameters)
    }
  }
}
