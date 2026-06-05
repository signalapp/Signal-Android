/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.remotebackuprestore

import org.signal.core.models.AccountEntropyPool
import org.signal.registration.util.DebugLoggable
import org.signal.registration.util.DebugLoggableModel

data class RemoteBackupRestoreState(
  val aep: AccountEntropyPool,
  val loadState: LoadState = LoadState.Loading,
  val backupTime: Long = -1,
  val backupSize: Long = 0,
  val restoreState: RestoreState = RestoreState.None,
  val restoreProgress: RestoreProgress? = null,
  val loadAttempts: Int = 0
) : DebugLoggableModel() {

  enum class LoadState {
    Loading,
    Loaded,
    NotFound,
    Failure
  }

  sealed interface RestoreState : DebugLoggable {
    data object None : RestoreState
    data object InProgress : RestoreState
    data object Restored : RestoreState
    data object NetworkFailure : RestoreState
    data object InvalidBackupVersion : RestoreState
    data object PermanentSvrBFailure : RestoreState
    data object Failed : RestoreState
  }

  data class RestoreProgress(
    val phase: Phase,
    val bytesCompleted: Long,
    val totalBytes: Long
  ) : DebugLoggableModel() {
    val progress: Float
      get() = if (totalBytes > 0) bytesCompleted.toFloat() / totalBytes.toFloat() else 0f

    enum class Phase {
      Downloading,
      Restoring,
      Finalizing
    }
  }
}
