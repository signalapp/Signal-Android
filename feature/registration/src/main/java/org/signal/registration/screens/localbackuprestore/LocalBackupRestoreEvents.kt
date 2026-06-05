/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.net.Uri
import org.signal.registration.util.DebugLoggableModel

sealed class LocalBackupRestoreEvents : DebugLoggableModel() {
  /** User tapped the button to pick a backup folder. */
  data object PickBackupFolder : LocalBackupRestoreEvents()

  /** User selected a backup folder via the folder picker. */
  data class BackupFolderSelected(val uri: Uri) : LocalBackupRestoreEvents()

  /** User wants to restore the found backup (navigates to credential entry). */
  data object RestoreBackup : LocalBackupRestoreEvents()

  /** User wants to choose a different folder. */
  data object ChooseDifferentFolder : LocalBackupRestoreEvents()

  /** User selected a specific backup from the backup picker. */
  data class BackupSelected(val backup: LocalBackupInfo) : LocalBackupRestoreEvents()

  /** A credential (passphrase or AEP) was received from the credential entry screen. */
  data class PassphraseSubmitted(val credential: String) : LocalBackupRestoreEvents()

  /** The folder picker was dismissed without selecting a folder. */
  data object FolderPickerDismissed : LocalBackupRestoreEvents()

  /** User wants to cancel. */
  data object Cancel : LocalBackupRestoreEvents()
}
