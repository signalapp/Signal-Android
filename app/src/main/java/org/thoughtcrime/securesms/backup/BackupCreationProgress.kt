/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

import org.signal.appsettings.backups.BackupCreationProgress
import org.thoughtcrime.securesms.keyvalue.protos.LocalBackupCreationProgress

val LocalBackupCreationProgress.isIdle: Boolean
  get() = idle != null || succeeded != null || failed != null || canceled != null || (exporting == null && transferring == null)

fun LocalBackupCreationProgress.toBackupCreationProgress(): BackupCreationProgress {
  val exporting = exporting
  val transferring = transferring

  return when {
    succeeded != null -> BackupCreationProgress.Succeeded
    failed != null -> BackupCreationProgress.Failed
    canceled != null -> BackupCreationProgress.Canceled
    exporting != null -> BackupCreationProgress.Exporting(
      phase = exporting.phase.toExportPhase(),
      frameExportCount = exporting.frameExportCount,
      frameTotalCount = exporting.frameTotalCount
    )
    transferring != null -> BackupCreationProgress.Transferring(
      completed = transferring.completed,
      total = transferring.total
    )
    else -> BackupCreationProgress.Idle
  }
}

private fun LocalBackupCreationProgress.ExportPhase.toExportPhase(): BackupCreationProgress.ExportPhase {
  return when (this) {
    LocalBackupCreationProgress.ExportPhase.NONE -> BackupCreationProgress.ExportPhase.NONE
    LocalBackupCreationProgress.ExportPhase.INITIALIZING -> BackupCreationProgress.ExportPhase.INITIALIZING
    LocalBackupCreationProgress.ExportPhase.ACCOUNT -> BackupCreationProgress.ExportPhase.ACCOUNT
    LocalBackupCreationProgress.ExportPhase.RECIPIENT -> BackupCreationProgress.ExportPhase.RECIPIENT
    LocalBackupCreationProgress.ExportPhase.THREAD -> BackupCreationProgress.ExportPhase.THREAD
    LocalBackupCreationProgress.ExportPhase.CALL -> BackupCreationProgress.ExportPhase.CALL
    LocalBackupCreationProgress.ExportPhase.STICKER -> BackupCreationProgress.ExportPhase.STICKER
    LocalBackupCreationProgress.ExportPhase.NOTIFICATION_PROFILE -> BackupCreationProgress.ExportPhase.NOTIFICATION_PROFILE
    LocalBackupCreationProgress.ExportPhase.CHAT_FOLDER -> BackupCreationProgress.ExportPhase.CHAT_FOLDER
    LocalBackupCreationProgress.ExportPhase.MESSAGE -> BackupCreationProgress.ExportPhase.MESSAGE
    LocalBackupCreationProgress.ExportPhase.FINALIZING -> BackupCreationProgress.ExportPhase.FINALIZING
  }
}
