/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.backups

/**
 * Where a local backup or chat export currently is, as far as the settings screens care.
 */
sealed interface BackupCreationProgress {

  data object Idle : BackupCreationProgress

  data object Canceled : BackupCreationProgress

  data object Succeeded : BackupCreationProgress

  data object Failed : BackupCreationProgress

  data class Exporting(
    val phase: ExportPhase,
    val frameExportCount: Long,
    val frameTotalCount: Long
  ) : BackupCreationProgress {
    val fraction: Float
      get() = if (frameTotalCount == 0L) 0f else frameExportCount / frameTotalCount.toFloat()
  }

  data class Transferring(
    val completed: Long,
    val total: Long
  ) : BackupCreationProgress {
    val fraction: Float
      get() = if (total == 0L) 0f else completed / total.toFloat()
  }

  enum class ExportPhase {
    NONE,
    INITIALIZING,
    ACCOUNT,
    RECIPIENT,
    THREAD,
    CALL,
    STICKER,
    NOTIFICATION_PROFILE,
    CHAT_FOLDER,
    MESSAGE,
    FINALIZING
  }

  val isIdle: Boolean
    get() = this !is Exporting && this !is Transferring
}
