/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

/**
 * Unified progress model for backup creation, shared across all backup destinations
 * (remote encrypted, local encrypted, local plaintext).
 *
 * The export phase is identical regardless of destination — the same data is serialized.
 * The transfer phase differs: remote uploads to CDN, local writes to disk.
 */
sealed interface BackupCreationProgress {
  data object Idle : BackupCreationProgress
  data object Canceled : BackupCreationProgress

  /**
   * The backup is being exported from the database into a serialized format.
   */
  data class Exporting(
    val phase: ExportPhase,
    val frameExportCount: Long = 0,
    val frameTotalCount: Long = 0
  ) : BackupCreationProgress

  /**
   * Post-export phase: the backup file and/or media are being transferred to their destination.
   * For remote backups this means uploading; for local backups this means writing to disk.
   *
   * [completed] and [total] are unitless — they may represent bytes (remote upload) or
   * item counts (local attachment export). The ratio [completed]/[total] yields progress.
   */
  data class Transferring(
    val completed: Long,
    val total: Long,
    val mediaPhase: Boolean
  ) : BackupCreationProgress

  enum class ExportPhase {
    NONE,
    ACCOUNT,
    RECIPIENT,
    THREAD,
    CALL,
    STICKER,
    NOTIFICATION_PROFILE,
    CHAT_FOLDER,
    MESSAGE
  }

  fun exportProgress(): Float {
    return when (this) {
      is Exporting -> if (frameTotalCount == 0L) 0f else frameExportCount / frameTotalCount.toFloat()
      else -> 0f
    }
  }

  fun transferProgress(): Float {
    return when (this) {
      is Transferring -> if (total == 0L) 0f else completed / total.toFloat()
      else -> 0f
    }
  }
}
