/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import java.math.BigDecimal
import java.util.Currency

class DonationReceiptListLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `loading`() = assertPassesLayoutChecks {
    DonationReceiptListScreen(state = DonationReceiptListState(), onEvent = {})
  }

  @Test
  fun `no receipts`() = assertPassesLayoutChecks {
    DonationReceiptListScreen(state = DonationReceiptListState(receipts = emptyList()), onEvent = {})
  }

  @Test
  fun `receipts across years`() = assertPassesLayoutChecks {
    DonationReceiptListScreen(
      state = DonationReceiptListState(
        receipts = listOf(
          receipt(1L, InAppPaymentReceiptRecord.Type.RECURRING_DONATION, 1_760_000_000_000L, "1234567.89", "JPY"),
          receipt(2L, InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION, 1_740_000_000_000L, "250.00", "EUR"),
          receipt(3L, InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT, 1_700_000_000_000L, "5.00", "USD")
        )
      ),
      onEvent = {}
    )
  }

  private fun receipt(id: Long, type: InAppPaymentReceiptRecord.Type, timestamp: Long, amount: String, currency: String): InAppPaymentReceiptRecord {
    return InAppPaymentReceiptRecord(id = id, amount = FiatMoney(BigDecimal(amount), Currency.getInstance(currency)), timestamp = timestamp, type = type, subscriptionLevel = 500)
  }
}
