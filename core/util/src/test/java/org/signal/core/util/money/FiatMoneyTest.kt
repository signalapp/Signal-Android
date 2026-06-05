package org.signal.core.util.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

class FiatMoneyTest {
  @Test
  fun given100USD_whenIGetDefaultPrecisionString_thenIExpect100dot00() {
    // GIVEN
    val fiatMoney = FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("USD"))

    // WHEN
    val result = fiatMoney.getDefaultPrecisionString(Locale.US)

    // THEN
    assertEquals("100.00", result)
  }

  @Test
  fun given100USD_whenIGetMinimumUnitPrecisionString_thenIExpect10000() {
    // GIVEN
    val fiatMoney = FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("USD"))

    // WHEN
    val result = fiatMoney.minimumUnitPrecisionString

    // THEN
    assertEquals("10000", result)
  }

  @Test
  fun given100JPY_whenIGetDefaultPrecisionString_thenIExpect100() {
    // GIVEN
    val fiatMoney = FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("JPY"))

    // WHEN
    val result = fiatMoney.getDefaultPrecisionString(Locale.US)

    // THEN
    assertEquals("100", result)
  }

  @Test
  fun given100JPY_whenIGetMinimumUnitPrecisionString_thenIExpect100() {
    // GIVEN
    val fiatMoney = FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("JPY"))

    // WHEN
    val result = fiatMoney.minimumUnitPrecisionString

    // THEN
    assertEquals("100", result)
  }

  @Test
  fun given100UGX_whenIGetDefaultPrecisionString_thenIExpect100() {
    // GIVEN
    val fiatMoney = FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("UGX"))

    // WHEN
    val result = fiatMoney.getDefaultPrecisionString(Locale.US)

    // THEN
    assertEquals("100", result)
  }

  @Test
  fun given100UGX_whenIGetMinimumUnitPrecisionString_thenIExpect10000() {
    // GIVEN
    val fiatMoney = FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("UGX"))

    // WHEN
    val result = fiatMoney.minimumUnitPrecisionString

    // THEN
    assertEquals("10000", result)
  }

  @Test
  fun given100ISK_whenIGetDefaultPrecisionString_thenIExpect100() {
    // GIVEN
    val fiatMoney = FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("ISK"))

    // WHEN
    val result = fiatMoney.getDefaultPrecisionString(Locale.US)

    // THEN
    assertEquals("100", result)
  }

  @Test
  fun given100ISK_whenIGetMinimumUnitPrecisionString_thenIExpect10000() {
    // GIVEN
    val fiatMoney = FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("ISK"))

    // WHEN
    val result = fiatMoney.minimumUnitPrecisionString

    // THEN
    assertEquals("10000", result)
  }

  @Test
  fun given899HUF_whenIGetMinimumUnitPrecisionString_thenIExpect89900() {
    // GIVEN
    val fiatMoney = FiatMoney(BigDecimal.valueOf(899), Currency.getInstance("HUF"))

    // WHEN
    val result = fiatMoney.minimumUnitPrecisionString

    // THEN
    assertEquals("89900", result)
  }

  @Test
  fun given89900NetworkHUF_whenIConvertFromSignalNetworkAmount_thenIExpect899() {
    // GIVEN
    val amount = BigDecimal.valueOf(89900)
    val currency = Currency.getInstance("HUF")

    // WHEN
    val result = FiatMoney.fromSignalNetworkAmount(amount, currency)

    // THEN
    assertEquals(BigDecimal.valueOf(899).setScale(2), result.amount.setScale(2))
    assertEquals(currency, result.currency)
  }
}
