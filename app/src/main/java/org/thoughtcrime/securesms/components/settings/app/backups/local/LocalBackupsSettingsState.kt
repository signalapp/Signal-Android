/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.settings.app.backups.local

/**
 * Immutable state for the on-device (legacy) backups settings screen.
 *
 * This is intended to be the single source of truth for UI rendering (i.e. a single `StateFlow`
 * emission fully describes what the screen should display).
 */
data class LocalBackupsSettingsState(
  val backupsEnabled: Boolean = false,
  val canTurnOn: Boolean = true,
  val lastBackupLabel: String? = null,
  val folderDisplayName: String? = null,
  val scheduleTimeLabel: String? = null,
  val progress: BackupProgressState = BackupProgressState.Idle
)
