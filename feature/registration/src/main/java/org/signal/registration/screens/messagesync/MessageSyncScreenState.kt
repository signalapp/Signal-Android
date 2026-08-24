/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

import org.signal.core.util.ByteSize

/**
 * UI state for the message-sync screen.
 *
 * [stage] is the single source of truth for which phase of the link-and-sync restore we're showing.
 * [showSyncFailedDialog] is an independent overlay shown when the restore fails.
 */
data class MessageSyncScreenState(
  val stage: Stage = Stage.Preparing,
  val showSyncFailedDialog: Boolean = false
) {

  /** Whether we've moved past cancelable progress into non-cancelable finalization. */
  val isFinishing: Boolean
    get() = stage is Stage.Finishing

  /** The phase of the restore currently surfaced to the user. */
  sealed interface Stage {
    /** Waiting for the first progress event; shown as an indeterminate bar. */
    data object Preparing : Stage

    /** Downloading the backup from the primary/CDN; shown as determinate progress. */
    data class Downloading(val downloaded: ByteSize, val total: ByteSize) : Stage

    /** Importing messages from the downloaded backup; shown as determinate progress. */
    data class Restoring(val restored: ByteSize, val total: ByteSize) : Stage

    /** Post-restore finalization that doesn't report granular progress; shown as an indeterminate bar. */
    data object Finishing : Stage
  }
}
