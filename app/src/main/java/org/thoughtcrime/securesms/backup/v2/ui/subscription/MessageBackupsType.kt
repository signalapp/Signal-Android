/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier

/**
 * Represents a type of backup a user can select.
 */
@Stable
data class MessageBackupsType(
  val tier: MessageBackupTier,
  val pricePerMonth: FiatMoney,
  val title: String,
  val features: ImmutableList<MessageBackupsTypeFeature>
)
