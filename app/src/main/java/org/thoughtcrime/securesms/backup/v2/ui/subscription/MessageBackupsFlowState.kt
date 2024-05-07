/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayResponse
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType

data class MessageBackupsFlowState(
  val selectedMessageBackupTier: MessageBackupTier? = null,
  val currentMessageBackupTier: MessageBackupTier? = null,
  val availableBackupTiers: List<MessageBackupTier> = emptyList(),
  val selectedPaymentGateway: GatewayResponse.Gateway? = null,
  val availablePaymentGateways: List<GatewayResponse.Gateway> = emptyList(),
  val pin: String = "",
  val pinKeyboardType: PinKeyboardType = SignalStore.pinValues().keyboardType
)
