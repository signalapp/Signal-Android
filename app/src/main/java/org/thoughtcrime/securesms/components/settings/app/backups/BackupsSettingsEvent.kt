/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import org.thoughtcrime.securesms.backup.v2.MessageBackupTier

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface BackupsSettingsEvent {

  /** The user tapped the navigation (close) icon. */
  data object NavigateBackClicked : BackupsSettingsEvent

  /** The user tapped the Signal Secure Backups row. */
  data object BackupsRowClicked : BackupsSettingsEvent

  /** The user tapped the on-device backups row. */
  data object OnDeviceBackupsRowClicked : BackupsSettingsEvent

  /** The user picked [tier] (or none) in the staging-only override row. */
  data class BackupTierInternalOverrideChanged(val tier: MessageBackupTier?) : BackupsSettingsEvent

  /** The user tapped the learn more link shown to linked devices. */
  data object LearnMoreClicked : BackupsSettingsEvent

  /** The sheet shown after a successful checkout was dismissed, so the user can be taken to their new backup. */
  data object CheckoutCompleted : BackupsSettingsEvent
}
