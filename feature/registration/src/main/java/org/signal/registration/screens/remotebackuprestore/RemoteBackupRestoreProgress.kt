/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.remotebackuprestore

import org.signal.libsignal.zkgroup.profiles.ProfileKey

/**
 * Progress events emitted during a remote backup restore operation.
 * Each value directly maps to a UI state for the progress dialog.
 */
sealed interface RemoteBackupRestoreProgress {
  /** Downloading the backup from the server. */
  data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : RemoteBackupRestoreProgress

  /** Importing/restoring messages from the downloaded backup. */
  data class Restoring(val bytesRead: Long, val totalBytes: Long) : RemoteBackupRestoreProgress

  /** Finalizing the restore (post-import cleanup). */
  data object Finalizing : RemoteBackupRestoreProgress

  /**
   * Restore completed successfully.
   * Provides registration-relevant data that was restored so that it isn't accidentally overridden.
   *
   * If any of the args are null, we will assume that they were unavailable in the backup, and will defer to
   * values generated during registration.
   */
  data class Complete(
    val restoredSvrPin: String?,
    val restoredProfileKey: ProfileKey?
  ) : RemoteBackupRestoreProgress

  /** Restore failed due to a network error (e.g. connection lost during download). */
  data class NetworkError(val cause: Throwable? = null) : RemoteBackupRestoreProgress

  /**
   * The backup was created by a newer version of Signal than this client supports.
   * The user should be prompted to update.
   */
  data object InvalidBackupVersion : RemoteBackupRestoreProgress

  /**
   * SVR-B has failed permanently, meaning the backup cannot be recovered.
   * The user should be prompted to contact support.
   */
  data object PermanentSvrBFailure : RemoteBackupRestoreProgress

  /** Restore failed for an unknown or generic reason. */
  data class GenericError(val cause: Throwable? = null) : RemoteBackupRestoreProgress

  /** The restore was canceled by the user or system. */
  data object Canceled : RemoteBackupRestoreProgress
}
