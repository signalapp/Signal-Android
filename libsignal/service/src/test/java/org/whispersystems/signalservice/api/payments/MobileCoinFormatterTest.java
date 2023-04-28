package org.whispersystems.signalservice.api.payments;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class MobileCoinFormatterTest {

  private static final Currency currency = Money.MobileCoin.CURRENCY;

  @Test
  public void givenAMoneyCurrency_whenIFormatWithDefaultOptions_thenIExpectADefaultFormattedString() {
    // GIVEN
    Formatter formatter = Formatter.forMoney(currency, FormatterOptions.defaults(Locale.US));

    // WHEN
    String result = formatter.format(BigDecimal.ONE);

    // THEN
    assertEquals("1 MOB", result);
  }

  @Test
  public void givenALargeMoneyCurrency_whenIFormatWithDefaultOptions_thenIExpectGrouping() {
    // GIVEN
    Formatter formatter = Formatter.forMoney(currency, FormatterOptions.defaults(Locale.US));

    // WHEN
    String result = formatter.format(BigDecimal.valueOf(-1000L));

    // THEN
    assertEquals("-1,000 MOB", result);
  }

  @Test
  public void givenANegative_whenIFormatWithAlwaysPositive_thenIExpectPositive() {
    // GIVEN
    FormatterOptions options   = FormatterOptions.builder(Locale.US).alwaysPositive().build();
    Formatter        formatter = Formatter.forMoney(currency, options);

    // WHEN
    String result = formatter.format(BigDecimal.valueOf(-100L));

    // THEN
    assertEquals("100 MOB", result);
  }

  @Test
  public void givenAnAmount_whenIFormatWithoutSpaceBeforeUnit_thenIExpectNoSpaceBeforeUnit() {
    // GIVEN
    FormatterOptions options   = FormatterOptions.builder(Locale.US).withoutSpaceBeforeUnit().build();
    Formatter        formatter = Formatter.forMoney(currency, options);

    // WHEN
    String result = formatter.format(BigDecimal.valueOf(100L));

    // THEN
    assertEquals("100MOB", result);
  }

  @Test
  public void givenAnAmount_whenIFormatWithoutUnit_thenIExpectNoSpaceBeforeUnit() {
    // GIVEN
    FormatterOptions options   = FormatterOptions.builder(Locale.US).withoutUnit().build();
    Formatter        formatter = Formatter.forMoney(currency, options);

    // WHEN
    String result = formatter.format(BigDecimal.valueOf(100L));

    // THEN
    assertEquals("100", result);
  }

  @Test
  public void givenAnAmount_whenIFormatWithAlwaysPrefixSign_thenIExpectSignOnPositiveValues() {
    // GIVEN
    FormatterOptions options   = FormatterOptions.builder(Locale.US).alwaysPrefixWithSign().build();
    Formatter        formatter = Formatter.forMoney(currency, options);

    // WHEN
    String result = formatter.format(BigDecimal.valueOf(100L));

    // THEN
    assertEquals("+100 MOB", result);
  }

  @Test
  public void givenAMoneyCurrency_whenIToStringWithDefaultOptions_thenIExpectADefaultFormattedString() {
    // GIVEN
    FormatterOptions options = FormatterOptions.defaults(Locale.US);

    // WHEN
    String result = Money.mobileCoin(BigDecimal.ONE).toString(options);

    // THEN
    assertEquals("1 MOB", result);
  }

  @Test
  public void givenAnAmount_whenIToStringWithAlwaysPrefixSign_thenIExpectSignOnPositiveValues() {
    // GIVEN
    FormatterOptions options = FormatterOptions.builder(Locale.US).alwaysPrefixWithSign().build();

    // WHEN
    String result = Money.mobileCoin(BigDecimal.ONE).toString(options);

    // THEN
    assertEquals("+1 MOB", result);
  }

  @Test
  public void givenAnAmount_whenIToStringWithMaximumFractionalDigitsOf4_thenIExpectRoundingAndTruncating() {
    // GIVEN
    FormatterOptions options = FormatterOptions.builder(Locale.US).withMaximumFractionDigits(4).build();

    // WHEN
    String result = Money.mobileCoin(BigDecimal.valueOf(1.1234567)).toString(options);

    // THEN
    assertEquals("1.1235 MOB", result);
  }

  @Test
  public void givenAnAmount_whenIToStringWithMaximumFractionalDigitsOf5_thenIExpectToSee5Places() {
    // GIVEN
    FormatterOptions options = FormatterOptions.builder(Locale.US).withMaximumFractionDigits(5).build();

    // WHEN
    String result = Money.mobileCoin(BigDecimal.valueOf(1.1234507)).toString(options);

    // THEN
    assertEquals("1.12345 MOB", result);
  }

  @Test
  public void givenAnAmount_whenIToStringWithMaximumFractionalDigitsOf5ButFewerActual_thenIExpectToSeeFewerPlaces() {
    // GIVEN
    FormatterOptions options = FormatterOptions.builder(Locale.US).withMaximumFractionDigits(5).build();

    // WHEN
    String result = Money.mobileCoin(BigDecimal.valueOf(1.120003)).toString(options);

    // THEN
    assertEquals("1.12 MOB", result);
  }
}
