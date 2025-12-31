package org.signal.core.util.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency

class FiatMoneyTest {
  @Test
  fun given100USD_whenIGetDefaultPrecisionString_thenIExpect100dot00() {
    // GIVEN
    val fiatMoney = FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("USD"))

    // WHEN
    val result = fiatMoney.defaultPrecisionString

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
    val result = fiatMoney.defaultPrecisionString

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
    val result = fiatMoney.defaultPrecisionString

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
    val result = fiatMoney.defaultPrecisionString

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
}
