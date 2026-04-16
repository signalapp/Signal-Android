/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.remotebackuprestore

import org.signal.registration.util.DebugLoggableModel

sealed class RemoteBackupRestoreScreenEvents : DebugLoggableModel() {
  data object BackupRestoreBackup : RemoteBackupRestoreScreenEvents()
  data object Retry : RemoteBackupRestoreScreenEvents()
  data object Cancel : RemoteBackupRestoreScreenEvents()
  data object DismissError : RemoteBackupRestoreScreenEvents()
}
