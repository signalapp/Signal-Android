package org.thoughtcrime.securesms.payments.currency;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.signal.core.util.money.FiatMoney;
import org.whispersystems.signalservice.api.payments.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Currency;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ExchangeRate_exchange {

  private final BigDecimal                    expected;
  private final CurrencyExchange.ExchangeRate exchange;
  private final Money                         money;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
            {BigDecimal.ONE, "USD", BigDecimal.valueOf(1.536), BigDecimal.valueOf(1.54).setScale(2, RoundingMode.UNNECESSARY)},
            {BigDecimal.ONE, "USD", BigDecimal.valueOf(1.535), BigDecimal.valueOf(1.54).setScale(2, RoundingMode.UNNECESSARY)},
            {BigDecimal.ONE, "USD", BigDecimal.valueOf(1.534), BigDecimal.valueOf(1.53).setScale(2, RoundingMode.UNNECESSARY)},

            {BigDecimal.ONE, "USD", BigDecimal.valueOf(1.526), BigDecimal.valueOf(1.53).setScale(2, RoundingMode.UNNECESSARY)},
            {BigDecimal.ONE, "USD", BigDecimal.valueOf(1.525), BigDecimal.valueOf(1.52).setScale(2, RoundingMode.UNNECESSARY)},
            {BigDecimal.ONE, "USD", BigDecimal.valueOf(1.524), BigDecimal.valueOf(1.52).setScale(2, RoundingMode.UNNECESSARY)},

            {BigDecimal.ONE, "USD", BigDecimal.valueOf(1.5), BigDecimal.valueOf(1.5).setScale(2, RoundingMode.UNNECESSARY)},
            {BigDecimal.ONE, "USD", BigDecimal.valueOf(1d), BigDecimal.valueOf(1).setScale(2, RoundingMode.UNNECESSARY)},

            {BigDecimal.ONE, "JPY", BigDecimal.valueOf(1.6), BigDecimal.valueOf(2).setScale(0, RoundingMode.UNNECESSARY)},
            {BigDecimal.ONE, "JPY", BigDecimal.valueOf(1.5), BigDecimal.valueOf(2).setScale(0, RoundingMode.UNNECESSARY)},
            {BigDecimal.ONE, "JPY", BigDecimal.valueOf(1.4), BigDecimal.valueOf(1).setScale(0, RoundingMode.UNNECESSARY)},

            {BigDecimal.ONE, "JPY", BigDecimal.valueOf(2.6), BigDecimal.valueOf(3).setScale(0, RoundingMode.UNNECESSARY)},
            {BigDecimal.ONE, "JPY", BigDecimal.valueOf(2.5), BigDecimal.valueOf(2).setScale(0, RoundingMode.UNNECESSARY)},
            {BigDecimal.ONE, "JPY", BigDecimal.valueOf(2.4), BigDecimal.valueOf(2).setScale(0, RoundingMode.UNNECESSARY)},

            {BigDecimal.ONE, "JPY", BigDecimal.valueOf(1d), BigDecimal.valueOf(1).setScale(0, RoundingMode.UNNECESSARY)},
    });
  }

  public ExchangeRate_exchange(@NonNull BigDecimal money,
                               @NonNull String currencyCode,
                               @NonNull BigDecimal rate,
                               @NonNull BigDecimal expected)
  {
    this.money    = Money.mobileCoin(money);
    this.exchange = new CurrencyExchange.ExchangeRate(Currency.getInstance(currencyCode), rate, 0);
    this.expected = expected;
  }

  @Test
  public void exchange() {
    FiatMoney amount = exchange.exchange(money).get();

    assertEquals(expected, amount.getAmount());
  }
}
