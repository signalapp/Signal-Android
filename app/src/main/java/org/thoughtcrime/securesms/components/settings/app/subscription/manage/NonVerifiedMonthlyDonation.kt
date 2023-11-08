/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import org.signal.core.util.money.FiatMoney

/**
 * Represents a monthly donation via iDEAL that is still pending user verification in
 * their 3rd party app.
 */
data class NonVerifiedMonthlyDonation(
  val timestamp: Long,
  val price: FiatMoney,
  val level: Int,
  val checkedVerification: Boolean
)
