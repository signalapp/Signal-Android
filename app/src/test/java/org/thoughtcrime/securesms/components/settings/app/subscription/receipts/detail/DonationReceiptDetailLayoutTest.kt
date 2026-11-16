/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import java.math.BigDecimal
import java.util.Currency

class DonationReceiptDetailLayoutTest : SignalLayoutStressTest() {

  private val largeGift = DonationReceiptDetailState(
    inAppPaymentReceiptRecord = InAppPaymentReceiptRecord(
      amount = FiatMoney(BigDecimal.valueOf(15_000_000), Currency.getInstance("IDR")),
      timestamp = 1760000000000L,
      type = InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT,
      subscriptionLevel = 0
    )
  )

  @Test
  fun `loading`() = assertPassesLayoutChecks {
    DonationReceiptDetailScreen(state = DonationReceiptDetailState(), onEvent = {})
  }

  @Test
  fun `large gift`() = assertPassesLayoutChecks {
    DonationReceiptDetailScreen(state = largeGift, onEvent = {})
  }
}
