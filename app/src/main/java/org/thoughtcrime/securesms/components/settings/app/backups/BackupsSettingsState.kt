/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Screen state for top-level backups settings screen.
 */
data class BackupsSettingsState(
  val backupState: BackupState,
  val lastBackupAt: Duration = SignalStore.backup.lastBackupTime.milliseconds,
  val showBackupTierInternalOverride: Boolean = false,
  val backupTierInternalOverride: MessageBackupTier? = null
)
