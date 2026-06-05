/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import org.signal.core.models.AccountEntropyPool

/**
 * Result communicated back from the pre-registration local backup restore flow
 * to the phone number entry screen via the result bus.
 */
sealed interface LocalBackupRestoreResult {
  /** The restore completed successfully. Contains the AEP if V2 backup, null if V1. */
  data class Success(val aep: AccountEntropyPool?) : LocalBackupRestoreResult

  /** The user canceled the restore flow. The pending restore option should be cleared. */
  data object Canceled : LocalBackupRestoreResult
}
