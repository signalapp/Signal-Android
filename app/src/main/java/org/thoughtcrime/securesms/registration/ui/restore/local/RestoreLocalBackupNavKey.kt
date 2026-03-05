/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface RestoreLocalBackupNavKey : NavKey {
  @Serializable
  object SelectLocalBackupTypeScreen : RestoreLocalBackupNavKey

  @Serializable
  object FolderInstructionSheet : RestoreLocalBackupNavKey

  @Serializable
  object FileInstructionSheet : RestoreLocalBackupNavKey

  @Serializable
  object SelectLocalBackupScreen : RestoreLocalBackupNavKey

  @Serializable
  object SelectLocalBackupSheet : RestoreLocalBackupNavKey

  @Serializable
  object EnterLocalBackupKeyScreen : RestoreLocalBackupNavKey

  @Serializable
  object NoRecoveryKeySheet : RestoreLocalBackupNavKey
}
