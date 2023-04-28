package org.whispersystems.signalservice.api.payments;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class FiatFormatterTest {

  private static final Currency javaCurrency = Currency.getInstance("USD");

  @Test
  public void givenAFiatCurrency_whenIFormatWithDefaultOptions_thenIExpectADefaultFormattedString() {
    // GIVEN
    Formatter formatter = Formatter.forFiat(javaCurrency, FormatterOptions.defaults(Locale.US));

    // WHEN
    String result = formatter.format(BigDecimal.valueOf(100));

    // THEN
    assertEquals("$100.00", result);
  }

  @Test
  public void givenANegative_whenIFormatWithAlwaysPositive_thenIExpectPositive() {
    // GIVEN
    FormatterOptions options   = FormatterOptions.builder(Locale.US).alwaysPositive().build();
    Formatter        formatter = Formatter.forFiat(javaCurrency, options);

    // WHEN
    String result = formatter.format(BigDecimal.valueOf(-100L));

    // THEN
    assertEquals("$100.00", result);
  }

  @Test
  public void givenALargeFiatCurrency_whenIFormatWithDefaultOptions_thenIExpectGrouping() {
    // GIVEN
    Formatter formatter = Formatter.forFiat(javaCurrency, FormatterOptions.defaults(Locale.US));

    // WHEN
    String result = formatter.format(BigDecimal.valueOf(1000L));

    // THEN
    assertEquals("$1,000.00", result);
  }

  @Test
  public void givenAFiatCurrency_whenIFormatWithoutUnit_thenIExpectAStringWithoutUnit() {
    // GIVEN
    Formatter formatter = Formatter.forFiat(javaCurrency, FormatterOptions.builder(Locale.US).withoutUnit().build());

    // WHEN
    String result = formatter.format(BigDecimal.ONE);

    // THEN
    assertEquals("1.00", result);
  }

}
