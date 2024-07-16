/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType

data class MessageBackupsFlowState(
  val selectedMessageBackupTier: MessageBackupTier? = SignalStore.backup.backupTier,
  val currentMessageBackupTier: MessageBackupTier? = SignalStore.backup.backupTier,
  val availableBackupTypes: List<MessageBackupsType> = emptyList(),
  val selectedPaymentMethod: InAppPaymentData.PaymentMethodType? = null,
  val availablePaymentMethods: List<InAppPaymentData.PaymentMethodType> = emptyList(),
  val pinKeyboardType: PinKeyboardType = SignalStore.pin.keyboardType,
  val inAppPayment: InAppPaymentTable.InAppPayment? = null,
  val startScreen: MessageBackupsScreen,
  val screen: MessageBackupsScreen = startScreen
)
