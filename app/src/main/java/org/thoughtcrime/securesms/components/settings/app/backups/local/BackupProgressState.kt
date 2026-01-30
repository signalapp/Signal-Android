/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.local

/**
 * Progress indicator state for the on-device backups creation/verification workflow.
 */
sealed class BackupProgressState {
  data object Idle : BackupProgressState()

  /**
   * Represents either backup creation or verification progress.
   *
   * @param summary High-level status label (e.g. "In progress…", "Verifying backup…")
   * @param percentLabel Secondary progress label (either a percent string or a count-based string)
   * @param progressFraction Optional progress fraction in \\([0, 1]\\). Null indicates indeterminate progress.
   */
  data class InProgress(
    val summary: String,
    val percentLabel: String,
    val progressFraction: Float?
  ) : BackupProgressState()
}
