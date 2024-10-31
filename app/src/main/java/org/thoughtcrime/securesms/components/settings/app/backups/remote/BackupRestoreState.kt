/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusData

/**
 * State container for BackupStatusData, including the enabled state.
 */
sealed interface BackupRestoreState {
  data object None : BackupRestoreState
  data class Ready(
    val bytes: String
  ) : BackupRestoreState
  data class FromBackupStatusData(
    val backupStatusData: BackupStatusData
  ) : BackupRestoreState
}
