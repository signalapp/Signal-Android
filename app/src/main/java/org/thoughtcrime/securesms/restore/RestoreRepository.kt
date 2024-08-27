/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.AppInitialization
import org.thoughtcrime.securesms.backup.BackupPassphrase
import org.thoughtcrime.securesms.backup.FullBackupImporter
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.impl.DataRestoreConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.service.LocalBackupListener
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.BackupUtil.BackupInfo
import java.io.IOException

/**
 * Repository to handle restoring a backup of a user's message history.
 */
object RestoreRepository {
  private val TAG = Log.tag(RestoreRepository.javaClass)

  suspend fun getLocalBackupFromUri(context: Context, uri: Uri): BackupInfoResult = withContext(Dispatchers.IO) {
    try {
      return@withContext BackupInfoResult(backupInfo = BackupUtil.getBackupInfoFromSingleUri(context, uri), failureCause = null, failure = false)
    } catch (ex: BackupUtil.BackupFileException) {
      Log.w(TAG, "Encountered error while trying to read backup!", ex)
      return@withContext BackupInfoResult(backupInfo = null, failureCause = ex, failure = true)
    }
  }

  suspend fun restoreBackupAsynchronously(context: Context, backupFileUri: Uri, passphrase: String): BackupImportResult = withContext(Dispatchers.IO) {
    // TODO [regv2]: migrate this to a service
    try {
      Log.i(TAG, "Initiating backup restore.")
      DataRestoreConstraint.isRestoringData = true

      val database = SignalDatabase.backupDatabase

      BackupPassphrase.set(context, passphrase)

      if (!FullBackupImporter.validatePassphrase(context, backupFileUri, passphrase)) {
        Log.i(TAG, "Restore failed due to invalid passphrase.")
        return@withContext BackupImportResult.FAILURE_UNKNOWN
      }

      Log.i(TAG, "Passphrase validated.")

      FullBackupImporter.importFile(
        context,
        AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
        database,
        backupFileUri,
        passphrase
      )

      Log.i(TAG, "Backup importer complete.")

      SignalDatabase.runPostBackupRestoreTasks(database)
      NotificationChannels.getInstance().restoreContactNotificationChannels()

      if (BackupUtil.canUserAccessBackupDirectory(context)) {
        LocalBackupListener.setNextBackupTimeToIntervalFromNow(context)
        SignalStore.settings.isBackupEnabled = true
        LocalBackupListener.schedule(context)
      }

      AppInitialization.onPostBackupRestore(context)

      Log.i(TAG, "Backup restore complete.")
      return@withContext BackupImportResult.SUCCESS
    } catch (e: FullBackupImporter.DatabaseDowngradeException) {
      Log.w(TAG, "Failed due to the backup being from a newer version of Signal.", e)
      return@withContext BackupImportResult.FAILURE_VERSION_DOWNGRADE
    } catch (e: FullBackupImporter.ForeignKeyViolationException) {
      Log.w(TAG, "Failed due to foreign key constraint violations.", e)
      return@withContext BackupImportResult.FAILURE_FOREIGN_KEY
    } catch (e: IOException) {
      Log.w(TAG, "Restore failed due to unknown error!", e)
      return@withContext BackupImportResult.FAILURE_UNKNOWN
    } finally {
      DataRestoreConstraint.isRestoringData = false
    }
  }

  enum class BackupImportResult {
    SUCCESS,
    FAILURE_VERSION_DOWNGRADE,
    FAILURE_FOREIGN_KEY,
    FAILURE_UNKNOWN
  }

  data class BackupInfoResult(val backupInfo: BackupInfo?, val failureCause: BackupUtil.BackupFileException?, val failure: Boolean)
}
