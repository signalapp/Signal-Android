/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.net.Uri
import org.signal.core.models.AccountEntropyPool
import org.signal.registration.util.DebugLoggableModel

data class LocalBackupRestoreState(
  val restorePhase: RestorePhase = RestorePhase.SelectFolder,
  val backupInfo: LocalBackupInfo? = null,
  val allBackups: List<LocalBackupInfo> = emptyList(),
  val selectedFolderUri: Uri? = null,
  val progressFraction: Float = 0f,
  val errorMessage: String? = null,
  val launchFolderPicker: Boolean = false,
  val aep: AccountEntropyPool? = null,
  val v1Passphrase: String? = null
) : DebugLoggableModel() {

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

    /** Restore failed. */
    Error
  }
}
