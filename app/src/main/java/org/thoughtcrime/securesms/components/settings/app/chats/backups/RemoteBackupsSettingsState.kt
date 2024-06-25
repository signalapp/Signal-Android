/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups

import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.BackupV2Event
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType

data class RemoteBackupsSettingsState(
  val messageBackupsType: MessageBackupsType? = null,
  val canBackUpUsingCellular: Boolean = false,
  val backupSize: Long = 0,
  val backupsFrequency: BackupFrequency = BackupFrequency.DAILY,
  val lastBackupTimestamp: Long = 0,
  val dialog: Dialog = Dialog.NONE,
  val snackbar: Snackbar = Snackbar.NONE,
  val backupProgress: BackupV2Event? = null
) {
  enum class Dialog {
    NONE,
    TURN_OFF_AND_DELETE_BACKUPS,
    BACKUP_FREQUENCY,
    DELETING_BACKUP,
    BACKUP_DELETED
  }

  enum class Snackbar {
    NONE,
    BACKUP_DELETED_AND_TURNED_OFF,
    BACKUP_TYPE_CHANGED_AND_SUBSCRIPTION_CANCELLED,
    SUBSCRIPTION_CANCELLED,
    DOWNLOAD_COMPLETE
  }
}
