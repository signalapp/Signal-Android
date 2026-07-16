/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.archive

import org.signal.core.models.AccountEntropyPool
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.zkgroup.profiles.ProfileKey

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

  /**
   * The restore completed successfully.
   * Provides registration-relevant data that was restored so that it isn't accidentally overridden.
   *
   * If any of the args are null, we will assume that they were unavailable in the backup, and will defer to
   * values generated during registration.
   *
   * [restoredAccountEntropyPool], [restoredAciIdentityKey], and [restoredPniIdentityKey] are only populated for V1
   * backups restored before registration, where we want to preserve the device's existing keys rather than generating
   * new ones.
   */
  data class Complete(
    val restoredSvrPin: String?,
    val restoredProfileKey: ProfileKey?,
    val restoredAccountEntropyPool: AccountEntropyPool? = null,
    val restoredAciIdentityKey: IdentityKeyPair? = null,
    val restoredPniIdentityKey: IdentityKeyPair? = null
  ) : LocalBackupRestoreProgress

  /** The provided passphrase (V1) or recovery key (V2) could not decrypt the backup. */
  data object IncorrectCredential : LocalBackupRestoreProgress

  /** The restore failed with an error. */
  data class Error(val cause: Throwable) : LocalBackupRestoreProgress
}
