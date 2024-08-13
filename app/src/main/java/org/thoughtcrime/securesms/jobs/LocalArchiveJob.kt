package org.thoughtcrime.securesms.jobs

import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.Result
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.BackupFileIOError
import org.thoughtcrime.securesms.backup.FullBackupExporter.BackupCanceledException
import org.thoughtcrime.securesms.backup.v2.LocalBackupV2Event
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.service.GenericForegroundService
import org.thoughtcrime.securesms.service.NotificationController
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.StorageUtil
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

    val updater = ProgressUpdater()

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
      updater.notification = notification
      EventBus.getDefault().register(updater)
      notification?.setIndeterminateProgress()

      val stopwatch = Stopwatch("archive-export")

      val archiveFileSystem = if (BackupUtil.isUserSelectionRequired(context)) {
        val backupDirectoryUri = SignalStore.settings.signalBackupDirectory

        if (backupDirectoryUri == null || backupDirectoryUri.path == null) {
          throw IOException("Backup Directory has not been selected!")
        }

        ArchiveFileSystem.fromUri(context, backupDirectoryUri)
      } else {
        ArchiveFileSystem.fromFile(context, StorageUtil.getOrCreateBackupV2Directory())
      }

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
        try {
          val result = LocalArchiver.export(snapshotFileSystem, archiveFileSystem.filesFileSystem, stopwatch)
          Log.i(TAG, "Archive finished with result: $result")
          if (result !is org.signal.core.util.Result.Success) {
            return Result.failure()
          }
        } catch (e: Exception) {
          Log.w(TAG, "Unable to create local archive", e)
          return Result.failure()
        }

        stopwatch.split("archive-create")

        // todo [local-backup] verify local backup
        EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.PROGRESS_VERIFYING))
        val valid = true

        stopwatch.split("archive-verify")

        if (valid) {
          snapshotFileSystem.finalize()
          stopwatch.split("archive-finalize")
        } else {
          BackupFileIOError.VERIFICATION_FAILED.postNotification(context)
        }

        EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.FINISHED))

        stopwatch.stop(TAG)
      } catch (e: BackupCanceledException) {
        EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.FINISHED))
        Log.w(TAG, "Archive cancelled")
        throw e
      } catch (e: IOException) {
        Log.w(TAG, "Error during archive!", e)
        EventBus.getDefault().post(LocalBackupV2Event(LocalBackupV2Event.Type.FINISHED))
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

      archiveFileSystem.deleteUnusedFiles()
      stopwatch.split("delete-unused")

      stopwatch.stop(TAG)
    } finally {
      notification?.close()
      EventBus.getDefault().unregister(updater)
      updater.notification = null
    }

    return Result.success()
  }

  override fun onFailure() {
  }

  private class ProgressUpdater {
    var notification: NotificationController? = null

    private var previousType: LocalBackupV2Event.Type? = null

    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onEvent(event: LocalBackupV2Event) {
      val notification = notification ?: return

      if (previousType != event.type) {
        notification.replaceTitle(event.type.toString()) // todo [local-backup] use actual strings
        previousType = event.type
      }

      if (event.estimatedTotalCount == 0L) {
        notification.setIndeterminateProgress()
      } else {
        notification.setProgress(event.estimatedTotalCount, event.count)
      }
    }
  }

  class Factory : Job.Factory<LocalArchiveJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): LocalArchiveJob {
      return LocalArchiveJob(parameters)
    }
  }
}
