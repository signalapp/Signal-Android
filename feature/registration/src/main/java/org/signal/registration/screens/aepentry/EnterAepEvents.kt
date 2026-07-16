/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import org.signal.core.util.censor

sealed class EnterAepEvents {
  /** User changed the backup key text. */
  data class BackupKeyChanged(val value: String) : EnterAepEvents() {
    override fun toString(): String = "BackupKeyChanged(value=${value.censor()})"
  }

  /** User submitted the backup key. */
  data object Submit : EnterAepEvents()

  /** User wants to cancel / no recovery key. */
  data object Cancel : EnterAepEvents()

  /** Dismiss a registration error dialog. */
  data object DismissError : EnterAepEvents()

  /** User confirmed restoring a backup that belongs to a different account, deferring the restore until after SMS verification. */
  data object ConfirmDifferentAccountRestore : EnterAepEvents()

  /** User dismissed the different-account warning dialog without restoring. */
  data object DismissDifferentAccountDialog : EnterAepEvents()
}
