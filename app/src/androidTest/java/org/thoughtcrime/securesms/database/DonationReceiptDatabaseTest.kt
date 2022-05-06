package org.thoughtcrime.securesms.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord
import java.math.BigDecimal
import java.util.Currency

class DonationReceiptDatabaseTest {

  private val records = listOf(
    DonationReceiptRecord.createForBoost(FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("USD"))),
    DonationReceiptRecord.createForBoost(FiatMoney(BigDecimal.valueOf(200), Currency.getInstance("USD")))
  )

  @Test
  fun givenNoReceipts_whenICheckHasReceipts_thenIExpectFalse() {
    assertFalse(SignalDatabase.donationReceipts.hasReceipts())
  }

  @Test
  fun givenOneReceipt_whenICheckHasReceipts_thenIExpectTrue() {
    SignalDatabase.donationReceipts.addReceipt(records.first())
    assertTrue(SignalDatabase.donationReceipts.hasReceipts())
  }

  @Test
  fun givenMultipleReceipts_whenICheckHasReceipts_thenIExpectTrue() {
    records.forEach {
      SignalDatabase.donationReceipts.addReceipt(it)
    }

    assertTrue(SignalDatabase.donationReceipts.hasReceipts())
  }
}
