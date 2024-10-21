/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusData

/**
 * State container for BackupStatusData, including the enabled state.
 */
data class BackupRestoreState(
  val enabled: Boolean,
  val backupStatusData: BackupStatusData
)
