/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.runtime.Immutable
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeySaveState
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.AccountEntropyPool

@Immutable
data class MessageBackupsFlowState(
  val selectedMessageBackupTier: MessageBackupTier? = SignalStore.backup.backupTier,
  val currentMessageBackupTier: MessageBackupTier? = null,
  val allBackupTypes: List<MessageBackupsType> = emptyList(),
  val isBillingApiAvailable: Boolean = false,
  val inAppPayment: InAppPaymentTable.InAppPayment? = null,
  val startScreen: MessageBackupsStage,
  val stage: MessageBackupsStage = startScreen,
  val accountEntropyPool: AccountEntropyPool = SignalStore.account.accountEntropyPool,
  val failure: Throwable? = null,
  val paymentReadyState: PaymentReadyState = PaymentReadyState.NOT_READY,
  val backupKeySaveState: BackupKeySaveState? = null
) {
  enum class PaymentReadyState {
    NOT_READY,
    READY,
    FAILED
  }

  /**
   * Whether or not the 'next' button on the type selection screen is enabled.
   */
  fun isCheckoutButtonEnabled(): Boolean {
    return selectedMessageBackupTier in allBackupTypes.map { it.tier } &&
      selectedMessageBackupTier != currentMessageBackupTier &&
      paymentReadyState == PaymentReadyState.READY
  }
}
