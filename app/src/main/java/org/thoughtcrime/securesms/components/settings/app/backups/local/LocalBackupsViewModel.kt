/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.local

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.util.StorageUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.BackupPassphrase
import org.thoughtcrime.securesms.backup.LocalExportProgress
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyCredentialManagerHandler
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeySaveState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.LocalBackupCreationProgress
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.formatHours
import java.time.LocalTime
import java.util.Locale

/**
 * Unified data model backups. Shares the same schema and file breakout as remote backups/.
 */
class LocalBackupsViewModel : ViewModel(), BackupKeyCredentialManagerHandler {

  companion object {
    private val TAG = Log.tag(LocalBackupsViewModel::class)
  }

  private val internalSettingsState = MutableStateFlow(
    LocalBackupsSettingsState(
      backupsEnabled = SignalStore.backup.newLocalBackupsEnabled,
      folderDisplayName = getDisplayName(AppDependencies.application, SignalStore.backup.newLocalBackupsDirectory)
    )
  )

  private val internalBackupState = MutableStateFlow(LocalBackupsKeyState())

  val settingsState = internalSettingsState
  val backupState = internalBackupState

  init {
    val applicationContext = AppDependencies.application

    viewModelScope.launch {
      SignalStore.backup.newLocalBackupsEnabledFlow.collect { enabled ->
        internalSettingsState.update { it.copy(backupsEnabled = enabled) }
      }
    }

    viewModelScope.launch {
      SignalStore.backup.newLocalBackupsDirectoryFlow.collect { directory ->
        internalSettingsState.update { it.copy(folderDisplayName = getDisplayName(applicationContext, directory)) }
      }
    }

    viewModelScope.launch {
      SignalStore.backup.newLocalBackupsLastBackupTimeFlow.collect { lastBackupTime ->
        internalSettingsState.update { it.copy(lastBackupLabel = calculateLastBackupTimeString(applicationContext, lastBackupTime)) }
      }
    }

    viewModelScope.launch {
      LocalExportProgress.encryptedProgress.collect { progress ->
        internalSettingsState.update { it.copy(progress = progress) }
      }
    }
  }

  fun refreshSettingsState() {
    val context = AppDependencies.application
    val backupTime = LocalTime.of(SignalStore.settings.backupHour, SignalStore.settings.backupMinute).formatHours(context)
    val backupFrequency = SignalStore.settings.backupFrequency

    val userUnregistered = TextSecurePreferences.isUnauthorizedReceived(context) || !SignalStore.account.isRegistered
    val clientDeprecated = SignalStore.misc.isClientDeprecated
    val legacyLocalBackupsEnabled = SignalStore.settings.isBackupEnabled && BackupUtil.canUserAccessBackupDirectory(context)
    val canTurnOn = legacyLocalBackupsEnabled || (!userUnregistered && !clientDeprecated)

    if (SignalStore.backup.newLocalBackupsEnabled) {
      if (!BackupUtil.canUserAccessUnifiedBackupDirectory(context)) {
        Log.w(TAG, "Lost access to backup directory, disabling backups")
        SignalStore.backup.newLocalBackupsEnabled = false
        AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)
      }
    } else {
      AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)
    }

    internalSettingsState.update {
      it.copy(
        canTurnOn = canTurnOn,
        scheduleTimeLabel = backupTime,
        frequencyV1 = backupFrequency,
      )
    }
  }

  fun onBackupStarted() {
    LocalExportProgress.setEncryptedProgress(LocalBackupCreationProgress(exporting = LocalBackupCreationProgress.Exporting(phase = LocalBackupCreationProgress.ExportPhase.NONE)))
  }

  fun turnOffAndDelete(context: Context) {
    internalSettingsState.update { it.copy(isDeleting = true) }

    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        SignalStore.backup.newLocalBackupsEnabled = false
        val path = SignalStore.backup.newLocalBackupsDirectory
        SignalStore.backup.newLocalBackupsDirectory = null
        AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)
        BackupUtil.deleteUnifiedBackups(context, path)
      }

      internalSettingsState.update { it.copy(isDeleting = false) }
    }
  }

  override fun updateBackupKeySaveState(newState: BackupKeySaveState?) {
    internalBackupState.update { it.copy(keySaveState = newState) }
  }

  suspend fun handleUpgrade(context: Context) {
    if (SignalStore.settings.isBackupEnabled) {
      withContext(Dispatchers.IO) {
        AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)
        AppDependencies.jobManager.flush()

        SignalStore.backup.newLocalBackupsDirectory = SignalStore.settings.signalBackupDirectory?.toString()

        BackupPassphrase.set(context, null)
        SignalStore.settings.isBackupEnabled = false
        BackupUtil.deleteAllBackups()
      }
    }

    SignalStore.backup.newLocalBackupsEnabled = true
    LocalBackupJob.enqueueArchive(false)
  }
}

private fun getDisplayName(context: Context, directoryUri: String?): String? {
  if (directoryUri == null) {
    return null
  }
  return StorageUtil.getDisplayPath(context, Uri.parse(directoryUri))
}

private fun calculateLastBackupTimeString(context: Context, lastBackupTimestamp: Long): String {
  return if (lastBackupTimestamp > 0) {
    val relativeTime = DateUtils.getDatelessRelativeTimeSpanFormattedDate(
      context,
      Locale.getDefault(),
      lastBackupTimestamp
    )

    if (relativeTime.isRelative) {
      relativeTime.value
    } else {
      val day = DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), lastBackupTimestamp)
      val time = relativeTime.value

      context.getString(R.string.RemoteBackupsSettingsFragment__s_at_s, day, time)
    }
  } else {
    context.getString(R.string.RemoteBackupsSettingsFragment__never)
  }
}
