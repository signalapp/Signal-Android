/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class RemoteBackupsSettingsState(
  val backupsEnabled: Boolean,
  val canBackUpUsingCellular: Boolean = false,
  val backupState: BackupState = BackupState.Loading,
  val backupSize: Long = 0,
  val backupsFrequency: BackupFrequency = BackupFrequency.DAILY,
  val lastBackupTimestamp: Long = 0,
  val dialog: Dialog = Dialog.NONE,
  val snackbar: Snackbar = Snackbar.NONE
) {

  /**
   * Describes the state of the user's selected backup tier.
   */
  sealed interface BackupState {

    /**
     * User has no active backup tier, no tier history
     */
    data object None : BackupState

    /**
     * The exact backup state is being loaded from the network.
     */
    data object Loading : BackupState

    /**
     * User has a paid backup subscription pending redemption
     */
    data class Pending(
      val price: FiatMoney
    ) : BackupState

    /**
     * A backup state with a type and renewal time
     */
    sealed interface WithTypeAndRenewalTime : BackupState {
      val messageBackupsType: MessageBackupsType
      val renewalTime: Duration

      fun isActive(): Boolean = false
    }

    /**
     * User has an active paid backup. Pricing comes from the subscription object.
     */
    data class ActivePaid(
      override val messageBackupsType: MessageBackupsType.Paid,
      val price: FiatMoney,
      override val renewalTime: Duration
    ) : WithTypeAndRenewalTime {
      override fun isActive(): Boolean = true
    }

    /**
     * User has an active free backup.
     */
    data class ActiveFree(
      override val messageBackupsType: MessageBackupsType.Free,
      override val renewalTime: Duration = 0.seconds
    ) : WithTypeAndRenewalTime {
      override fun isActive(): Boolean = true
    }

    /**
     * User has an inactive backup
     */
    data class Inactive(
      override val messageBackupsType: MessageBackupsType,
      override val renewalTime: Duration = 0.seconds
    ) : WithTypeAndRenewalTime

    /**
     * User has a canceled paid tier backup
     */
    data class Canceled(
      override val messageBackupsType: MessageBackupsType,
      override val renewalTime: Duration
    ) : WithTypeAndRenewalTime

    /**
     * An error occurred retrieving the network state
     */
    data object Error : BackupState
  }

  enum class Dialog {
    NONE,
    TURN_OFF_AND_DELETE_BACKUPS,
    BACKUP_FREQUENCY,
    PROGRESS_SPINNER,
    DOWNLOADING_YOUR_BACKUP,
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
