/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.archive

/**
 * Represents the progress of a local backup restore operation.
 * Emitted as a flow from the storage controller during restore.
 */
sealed interface LocalBackupRestoreProgress {
  /** The restore is being prepared (e.g., reading metadata, validating). */
  data object Preparing : LocalBackupRestoreProgress

  /** The restore is actively in progress. */
  data class InProgress(
    val bytesRead: Long,
    val totalBytes: Long
  ) : LocalBackupRestoreProgress {
    val progressFraction: Float
      get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
  }

  /** The restore completed successfully. */
  data object Complete : LocalBackupRestoreProgress

  /** The restore failed with an error. */
  data class Error(val cause: Throwable) : LocalBackupRestoreProgress
}
