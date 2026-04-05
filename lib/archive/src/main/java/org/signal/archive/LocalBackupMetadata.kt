/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.archive

/**
 * Metadata about a local backup file available for restore.
 */
data class LocalBackupMetadata(
  /** Size of the backup in bytes. */
  val sizeBytes: Long,
  /** Timestamp when the backup was created, in milliseconds since epoch. */
  val timestampMs: Long
)
