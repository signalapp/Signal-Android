/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.AccountEntropyPool

data class MessageBackupsFlowState(
  val selectedMessageBackupTier: MessageBackupTier? = SignalStore.backup.backupTier,
  val currentMessageBackupTier: MessageBackupTier? = SignalStore.backup.backupTier,
  val availableBackupTypes: List<MessageBackupsType> = emptyList(),
  val inAppPayment: InAppPaymentTable.InAppPayment? = null,
  val startScreen: MessageBackupsStage,
  val stage: MessageBackupsStage = startScreen,
  val accountEntropyPool: AccountEntropyPool = SignalStore.account.accountEntropyPool,
  val failure: Throwable? = null,
  val paymentReadyState: PaymentReadyState = PaymentReadyState.NOT_READY
) {
  enum class PaymentReadyState {
    NOT_READY,
    READY,
    FAILED
  }
}
