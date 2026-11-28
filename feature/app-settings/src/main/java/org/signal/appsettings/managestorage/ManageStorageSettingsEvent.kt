/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface ManageStorageSettingsEvent {

  /** The screen came back to the foreground, so the storage usage and chat limits may be stale. */
  data object ScreenResumed : ManageStorageSettingsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : ManageStorageSettingsEvent

  /** The user tapped the review storage button under the usage breakdown. */
  data object ReviewStorageClicked : ManageStorageSettingsEvent

  /** The user flipped the optimize storage toggle. */
  data class OptimizeStorageToggled(val enabled: Boolean) : ManageStorageSettingsEvent

  /** The user confirmed turning on optimized storage even though it will remove media from their on-device backup. */
  data object OptimizeStorageWithLocalBackupConfirmed : ManageStorageSettingsEvent

  /** The user finished signing up for the paid backups tier from the upgrade sheet. */
  data object PaidTierUpgradeCompleted : ManageStorageSettingsEvent

  /** The user tapped the keep messages row. */
  data object KeepMessagesClicked : ManageStorageSettingsEvent

  /** The user tapped the chat length limit row. */
  data object ChatLengthLimitClicked : ManageStorageSettingsEvent

  /** The user flipped the toggle that applies chat limits to linked devices. */
  data class SyncTrimDeletesToggled(val enabled: Boolean) : ManageStorageSettingsEvent

  /** The user tapped the delete message history row, which asks them to confirm twice. */
  data object DeleteChatHistoryClicked : ManageStorageSettingsEvent

  /** The user confirmed whichever of the two delete message history dialogs is showing. */
  data object DeleteChatHistoryConfirmed : ManageStorageSettingsEvent

  /** Dismisses whatever is in [ManageStorageSettingsState.dialog]. */
  data object DialogDismissed : ManageStorageSettingsEvent
}
