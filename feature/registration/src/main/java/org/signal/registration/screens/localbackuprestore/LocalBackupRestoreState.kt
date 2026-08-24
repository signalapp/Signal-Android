/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.net.Uri
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.censor
import org.signal.registration.screens.shared.RestoreProgress

data class LocalBackupRestoreState(
  val restorePhase: RestorePhase = RestorePhase.SelectFolder,
  val backupInfo: LocalBackupInfo? = null,
  val allBackups: List<LocalBackupInfo> = emptyList(),
  val selectedFolderUri: Uri? = null,
  val progressFraction: Float = 0f,
  val restoreProgress: RestoreProgress? = null,
  val errorMessage: String? = null,
  val launchFolderPicker: Boolean = false,
  val aep: AccountEntropyPool? = null,
  val v1Passphrase: String? = null,
  val storageCapable: Boolean = true
) {

  override fun toString(): String = "LocalBackupRestoreState(restorePhase=$restorePhase, backupInfo=$backupInfo, allBackups=$allBackups, selectedFolderUri=$selectedFolderUri, progressFraction=$progressFraction, restoreProgress=$restoreProgress, errorMessage=$errorMessage, launchFolderPicker=$launchFolderPicker, aep=${aep?.displayValue?.censor()}, v1Passphrase=${v1Passphrase?.censor()}, storageCapable=$storageCapable)"

  enum class RestorePhase {
    /** Waiting for user to select a backup folder. */
    SelectFolder,

    /** Scanning the selected folder for backups. */
    Scanning,

    /** A backup was found and is being displayed. */
    BackupFound,

    /** No backups were found in the selected folder. */
    NoBackupFound,

    /** Preparing the restore (reading metadata, validating). */
    Preparing,

    /** Restore is actively in progress. */
    InProgress,

    /** The entered passphrase/recovery key could not decrypt the backup. */
    IncorrectCredential,

    /** Restore failed. */
    Error
  }
}
