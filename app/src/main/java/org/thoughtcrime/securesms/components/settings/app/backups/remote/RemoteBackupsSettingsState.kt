/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class RemoteBackupsSettingsState(
  val backupsInitialized: Boolean,
  val messageBackupsType: MessageBackupsType? = null,
  val canBackUpUsingCellular: Boolean = false,
  val backupState: BackupState = BackupState.LOADING,
  val backupSize: Long = 0,
  val backupsFrequency: BackupFrequency = BackupFrequency.DAILY,
  val lastBackupTimestamp: Long = 0,
  val renewalTime: Duration = 0.seconds,
  val dialog: Dialog = Dialog.NONE,
  val snackbar: Snackbar = Snackbar.NONE
) {
  /**
   * Describes the state of the user's selected backup tier.
   */
  enum class BackupState {
    /**
     * The exact backup state is being loaded from the network.
     */
    LOADING,

    /**
     * User has an active backup
     */
    ACTIVE,

    /**
     * User has an inactive paid tier backup
     */
    INACTIVE,

    /**
     * User has a canceled paid tier backup
     */
    CANCELED,

    /**
     * An error occurred retrieving the network state
     */
    ERROR
  }

  enum class Dialog {
    NONE,
    TURN_OFF_AND_DELETE_BACKUPS,
    BACKUP_FREQUENCY,
    DELETING_BACKUP,
    BACKUP_DELETED,
    TURN_OFF_FAILED
  }

  enum class Snackbar {
    NONE,
    BACKUP_DELETED_AND_TURNED_OFF,
    BACKUP_TYPE_CHANGED_AND_SUBSCRIPTION_CANCELLED,
    SUBSCRIPTION_CANCELLED,
    DOWNLOAD_COMPLETE,
    BACKUP_WILL_BE_CREATED_OVERNIGHT
  }
}
