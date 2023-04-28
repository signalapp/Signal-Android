package org.signal.core.util.money;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.Assert.assertEquals;

public class FiatMoneyTest {

  @Test
  public void given100USD_whenIGetDefaultPrecisionString_thenIExpect100dot00() {
    // GIVEN
    FiatMoney fiatMoney = new FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("USD"));

    // WHEN
    String result = fiatMoney.getDefaultPrecisionString();

    // THEN
    assertEquals("100.00", result);
  }

  @Test
  public void given100USD_whenIGetMinimumUnitPrecisionString_thenIExpect10000() {
    // GIVEN
    FiatMoney fiatMoney = new FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("USD"));

    // WHEN
    String result = fiatMoney.getMinimumUnitPrecisionString();

    // THEN
    assertEquals("10000", result);
  }

  @Test
  public void given100JPY_whenIGetDefaultPrecisionString_thenIExpect100() {
    // GIVEN
    FiatMoney fiatMoney = new FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("JPY"));

    // WHEN
    String result = fiatMoney.getDefaultPrecisionString();

    // THEN
    assertEquals("100", result);
  }

  @Test
  public void given100JPY_whenIGetMinimumUnitPrecisionString_thenIExpect100() {
    // GIVEN
    FiatMoney fiatMoney = new FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("JPY"));

    // WHEN
    String result = fiatMoney.getMinimumUnitPrecisionString();

    // THEN
    assertEquals("100", result);
  }

  @Test
  public void given100UGX_whenIGetDefaultPrecisionString_thenIExpect100() {
    // GIVEN
    FiatMoney fiatMoney = new FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("UGX"));

    // WHEN
    String result = fiatMoney.getDefaultPrecisionString();

    // THEN
    assertEquals("100", result);
  }

  @Test
  public void given100UGX_whenIGetMinimumUnitPrecisionString_thenIExpect10000() {
    // GIVEN
    FiatMoney fiatMoney = new FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("UGX"));

    // WHEN
    String result = fiatMoney.getMinimumUnitPrecisionString();

    // THEN
    assertEquals("10000", result);
  }

  @Test
  public void given100ISK_whenIGetDefaultPrecisionString_thenIExpect100() {
    // GIVEN
    FiatMoney fiatMoney = new FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("ISK"));

    // WHEN
    String result = fiatMoney.getDefaultPrecisionString();

    // THEN
    assertEquals("100", result);
  }

  @Test
  public void given100ISK_whenIGetMinimumUnitPrecisionString_thenIExpect10000() {
    // GIVEN
    FiatMoney fiatMoney = new FiatMoney(BigDecimal.valueOf(100), Currency.getInstance("ISK"));

    // WHEN
    String result = fiatMoney.getMinimumUnitPrecisionString();

    // THEN
    assertEquals("10000", result);
  }
}
