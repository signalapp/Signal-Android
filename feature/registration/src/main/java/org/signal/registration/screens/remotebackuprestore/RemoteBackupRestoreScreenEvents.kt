/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.remotebackuprestore

sealed class RemoteBackupRestoreScreenEvents {
  data object BackupRestoreBackup : RemoteBackupRestoreScreenEvents()

  data object Retry : RemoteBackupRestoreScreenEvents()

  data object Cancel : RemoteBackupRestoreScreenEvents()

  data object DismissError : RemoteBackupRestoreScreenEvents()
}
