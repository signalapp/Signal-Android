/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups.type

import androidx.compose.runtime.Stable
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType

@Stable
data class BackupsTypeSettingsState(
  val messageBackupsType: MessageBackupsType? = null,
  val paymentSourceType: PaymentSourceType = PaymentSourceType.Unknown,
  val nextRenewalTimestamp: Long = 0
)
