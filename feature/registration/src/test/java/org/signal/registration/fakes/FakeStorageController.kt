/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.fakes

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.RestoreDecision
import org.signal.registration.StorageController
import org.signal.registration.StoredProfileData
import org.signal.registration.proto.RegistrationData
import org.signal.registration.screens.localbackuprestore.LocalBackupInfo
import org.signal.registration.screens.messagesync.LinkAndSyncProgress
import org.signal.registration.screens.remotebackuprestore.RemoteBackupRestoreProgress
import java.time.LocalDateTime

/**
 * An in-memory [StorageController] representing a fresh, never-registered install by default.
 * Set [preExistingRegistrationData] or [storedProfileData] to simulate a device with prior state.
 *
 * Backup scanning and restore operations delegate to overridable `on<Method>` handlers whose defaults find a single
 * V2 backup and restore it successfully. Methods that no flow under test should reach fail loudly.
 */
class FakeStorageController : StorageController {

  var inProgressData: RegistrationData = RegistrationData()
    private set
  var committedData: RegistrationData? = null
    private set
  var restoreDecision: RestoreDecision? = null
    private set

  /** Simulates a previously-registered device, which the flow will try to re-register via recovery password. */
  var preExistingRegistrationData: PreExistingRegistrationData? = null

  /** Profile data already on disk, used to pre-seed or skip the create-profile screen. */
  var storedProfileData: StoredProfileData = StoredProfileData()

  // -- Response handlers. Override these in tests to change what is found on disk and how restores play out.

  var onScanLocalBackupFolder: suspend (folderUri: Uri) -> List<LocalBackupInfo> = { folderUri ->
    listOf(
      LocalBackupInfo(
        type = LocalBackupInfo.BackupType.V2,
        date = LocalDateTime.of(2026, 1, 1, 12, 0),
        name = "signal-backup-2026-01-01-12-00-00",
        uri = Uri.withAppendedPath(folderUri, "signal-backup-2026-01-01-12-00-00"),
        sizeBytes = 1024
      )
    )
  }

  var onRestoreLocalBackupV1: (uri: Uri, passphrase: String) -> Flow<LocalBackupRestoreProgress> = { _, _ ->
    flowOf(LocalBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null))
  }

  var onRestoreLocalBackupV2: (backupUri: Uri, aep: AccountEntropyPool) -> Flow<LocalBackupRestoreProgress> = { _, _ ->
    flowOf(LocalBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null))
  }

  var onVerifyLocalBackupKey: suspend (backupUri: Uri, aep: AccountEntropyPool) -> Boolean = { _, _ -> true }

  var onRestoreRemoteBackup: (aep: AccountEntropyPool) -> Flow<RemoteBackupRestoreProgress> = {
    flowOf(RemoteBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null))
  }

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? = preExistingRegistrationData

  override suspend fun clearAllData() {
    inProgressData = RegistrationData()
  }

  override suspend fun clearLocalDataAndRestart() = notExpected()

  override suspend fun readInProgressRegistrationData(): RegistrationData = inProgressData

  override suspend fun updateInProgressRegistrationData(updater: RegistrationData.Builder.() -> Unit) {
    inProgressData = inProgressData.newBuilder().apply(updater).lastUpdatedMillis(System.currentTimeMillis()).build()
  }

  override suspend fun commitRegistrationData() {
    val accountData = inProgressData.accountData
    val accountDataComplete = accountData != null && accountData.e164.isNotEmpty() && accountData.aci.isNotEmpty() && accountData.pni.isNotEmpty() && accountData.servicePassword.isNotEmpty()
    if (!inProgressData.accountDataCommitted && accountDataComplete) {
      inProgressData = inProgressData.newBuilder().accountDataCommitted(true).build()
    }
    committedData = inProgressData
  }

  override suspend fun setRestoreDecision(decision: RestoreDecision) {
    // Mirrors the real controller: only the first decision sticks, later ones are ignored
    if (restoreDecision == null) {
      restoreDecision = decision
    }
  }

  override fun restoreLocalBackupV1(rootUri: Uri, backupUri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress> {
    return onRestoreLocalBackupV1(backupUri, passphrase)
  }

  override fun restoreLocalBackupV2(rootUri: Uri, backupUri: Uri, aep: AccountEntropyPool): Flow<LocalBackupRestoreProgress> {
    return onRestoreLocalBackupV2(backupUri, aep)
  }

  override suspend fun verifyLocalBackupKey(backupUri: Uri, aep: AccountEntropyPool): Boolean {
    return onVerifyLocalBackupKey(backupUri, aep)
  }

  override fun restoreRemoteBackup(aep: AccountEntropyPool): Flow<RemoteBackupRestoreProgress> {
    return onRestoreRemoteBackup(aep)
  }

  override fun restoreLinkAndSyncBackup(cdn: Int, key: String): Flow<LinkAndSyncProgress> = notExpected()

  override suspend fun scanLocalBackupFolder(folderUri: Uri): List<LocalBackupInfo> {
    return onScanLocalBackupFolder(folderUri)
  }

  override suspend fun getStoredProfileData(): StoredProfileData = storedProfileData

  private fun notExpected(): Nothing {
    throw NotImplementedError("This method is not expected to be called in the flow under test.")
  }
}
