/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.runtime.Stable
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier

/**
 * Represents a type of backup a user can select.
 */
@Stable
sealed interface MessageBackupsType {

  val tier: MessageBackupTier

  data class Paid(
    val pricePerMonth: FiatMoney,
    val storageAllowanceBytes: Long
  ) : MessageBackupsType {
    override val tier: MessageBackupTier = MessageBackupTier.PAID
  }

  data class Free(
    val mediaRetentionDays: Int
  ) : MessageBackupsType {
    override val tier: MessageBackupTier = MessageBackupTier.FREE
  }
}
