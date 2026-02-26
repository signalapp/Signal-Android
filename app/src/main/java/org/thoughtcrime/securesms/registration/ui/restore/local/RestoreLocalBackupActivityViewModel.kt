/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.ByteSize
import org.signal.core.util.Result
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.jobs.RestoreLocalAttachmentJob
import org.thoughtcrime.securesms.keyvalue.Completed
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.ui.restore.StorageServiceRestore
import org.thoughtcrime.securesms.registration.util.RegistrationUtil

class RestoreLocalBackupActivityViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(RestoreLocalBackupActivityViewModel::class)
  }

  private val internalState = MutableStateFlow(RestoreLocalBackupScreenState())
  val state: StateFlow<RestoreLocalBackupScreenState> = internalState

  init {
    EventBus.getDefault().register(this)
    beginRestore()
  }

  override fun onCleared() {
    EventBus.getDefault().unregister(this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onRestoreEvent(event: RestoreV2Event) {
    internalState.update {
      when (event.type) {
        RestoreV2Event.Type.PROGRESS_RESTORE -> it.copy(
          restorePhase = RestorePhase.RESTORING,
          bytesRead = event.count,
          totalBytes = event.estimatedTotalCount,
          progress = event.getProgress()
        )

        RestoreV2Event.Type.PROGRESS_DOWNLOAD -> it.copy(
          restorePhase = RestorePhase.RESTORING,
          bytesRead = event.count,
          totalBytes = event.estimatedTotalCount,
          progress = event.getProgress()
        )

        RestoreV2Event.Type.PROGRESS_FINALIZING -> it.copy(
          restorePhase = RestorePhase.FINALIZING
        )
      }
    }
  }

  private fun beginRestore() {
    viewModelScope.launch(Dispatchers.IO) {
      internalState.update { it.copy(restorePhase = RestorePhase.RESTORING) }

      val self = Recipient.self()
      val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

      val backupDirectory = SignalStore.backup.newLocalBackupsDirectory
      if (backupDirectory == null) {
        Log.w(TAG, "No backup directory set")
        internalState.update { it.copy(restorePhase = RestorePhase.FAILED) }
        return@launch
      }

      val archiveFileSystem = ArchiveFileSystem.fromUri(AppDependencies.application, Uri.parse(backupDirectory))
      if (archiveFileSystem == null) {
        Log.w(TAG, "Unable to access backup directory: $backupDirectory")
        internalState.update { it.copy(restorePhase = RestorePhase.FAILED) }
        return@launch
      }

      val selectedTimestamp = SignalStore.backup.newLocalBackupsSelectedSnapshotTimestamp
      val snapshots = archiveFileSystem.listSnapshots()
      val snapshotInfo = snapshots.firstOrNull { it.timestamp == selectedTimestamp } ?: snapshots.firstOrNull()

      if (snapshotInfo == null) {
        Log.w(TAG, "No snapshots found in backup directory")
        internalState.update { it.copy(restorePhase = RestorePhase.FAILED) }
        return@launch
      }

      val snapshotFileSystem = SnapshotFileSystem(AppDependencies.application, snapshotInfo.file)
      val result = LocalArchiver.import(snapshotFileSystem, selfData)

      if (result is Result.Success) {
        Log.i(TAG, "Local backup import succeeded")
        val mediaNameToFileInfo = archiveFileSystem.filesFileSystem.allFiles()
        RestoreLocalAttachmentJob.enqueueRestoreLocalAttachmentsJobs(mediaNameToFileInfo)

        SignalStore.registration.restoreDecisionState = RestoreDecisionState.Completed
        SignalStore.backup.backupSecretRestoreRequired = false
        SignalStore.backup.newLocalBackupsSelectedSnapshotTimestamp = -1L
        SignalStore.backup.newLocalBackupsEnabled = true
        LocalBackupJob.enqueueArchive(false)
        StorageServiceRestore.restore()
        RegistrationUtil.maybeMarkRegistrationComplete()

        internalState.update { it.copy(restorePhase = RestorePhase.COMPLETE) }
      } else {
        Log.w(TAG, "Local backup import failed")
        internalState.update { it.copy(restorePhase = RestorePhase.FAILED) }
      }
    }
  }
}

data class RestoreLocalBackupScreenState(
  val restorePhase: RestorePhase = RestorePhase.RESTORING,
  val bytesRead: ByteSize = 0L.bytes,
  val totalBytes: ByteSize = 0L.bytes,
  val progress: Float = 0f
)

enum class RestorePhase {
  RESTORING,
  FINALIZING,
  COMPLETE,
  FAILED
}
