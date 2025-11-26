/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import org.signal.core.util.ByteSize
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.backups.BackupState
import org.thoughtcrime.securesms.keyvalue.BackupValues

/**
 * @param includeDebuglog The state for whether or not we should include a debuglog in the backup. If `null`, hide the setting.
 */
data class RemoteBackupsSettingsState(
  val tier: MessageBackupTier? = null,
  val backupsEnabled: Boolean,
  val canViewBackupKey: Boolean = false,
  val canBackUpUsingCellular: Boolean = false,
  val canRestoreUsingCellular: Boolean = false,
  val hasRedemptionError: Boolean = false,
  val isOutOfStorageSpace: Boolean = false,
  val isPaidTierPricingAvailable: Boolean = false,
  val totalAllowedStorageSpace: String = "",
  val backupState: BackupState,
  val backupMediaSize: Long = -1L,
  val lastBackupTimestamp: Long = 0,
  val dialog: Dialog = Dialog.NONE,
  val snackbar: Snackbar = Snackbar.NONE,
  val includeDebuglog: Boolean? = null,
  val canBackupMessagesJobRun: Boolean = false,
  val backupMediaDetails: BackupMediaDetails? = null,
  val backupCreationError: BackupValues.BackupCreationError? = null,
  val lastMessageCutoffTime: Long = 0,
  val freeTierMediaRetentionDays: Int = -1,
  val isGooglePlayServicesAvailable: Boolean = false
) {

  data class BackupMediaDetails(
    val awaitingRestore: ByteSize,
    val offloaded: ByteSize,
    val protoFileSize: ByteSize
  )

  enum class Dialog {
    NONE,
    TURN_OFF_AND_DELETE_BACKUPS,
    BACKUP_FREQUENCY,
    PROGRESS_SPINNER,
    DOWNLOADING_YOUR_BACKUP,
    TURN_OFF_FAILED,
    SUBSCRIPTION_NOT_FOUND,
    SKIP_MEDIA_RESTORE_PROTECTION,
    CANCEL_MEDIA_RESTORE_PROTECTION,
    RESTORE_OVER_CELLULAR_PROTECTION,
    FREE_TIER_MEDIA_EXPLAINER,
    KEY_ROTATION_LIMIT_REACHED
  }

  enum class Snackbar {
    NONE,
    BACKUP_DELETED_AND_TURNED_OFF,
    BACKUP_TYPE_CHANGED_AND_SUBSCRIPTION_CANCELLED,
    SUBSCRIPTION_CANCELLED,
    DOWNLOAD_COMPLETE,
    BACKUP_WILL_BE_CREATED_OVERNIGHT,
    AEP_KEY_ROTATED
  }
}
