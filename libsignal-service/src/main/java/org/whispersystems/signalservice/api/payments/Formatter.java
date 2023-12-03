package org.whispersystems.signalservice.api.payments;

import java.math.BigDecimal;
import java.text.NumberFormat;

/**
 * Formats the given amount to look like a given currency, utilizing the given formatter options.
 */
public abstract class Formatter {

  protected final FormatterOptions formatterOptions;

  private Formatter(FormatterOptions formatterOptions) {
    this.formatterOptions = formatterOptions;
  }

  public abstract String format(BigDecimal amount);

  static Formatter forMoney(Currency currency, FormatterOptions formatterOptions) {
    return new CryptoFormatter(currency, formatterOptions);
  }

  static Formatter forFiat(java.util.Currency currency, FormatterOptions formatterOptions) {
    return new FiatFormatter(currency, formatterOptions);
  }

  private static final class FiatFormatter extends Formatter {

    private final java.util.Currency javaCurrency;

    private FiatFormatter(java.util.Currency javaCurrency, FormatterOptions formatterOptions) {
      super(formatterOptions);

      this.javaCurrency = javaCurrency;
    }

    @Override
    public String format(BigDecimal amount) {
      BigDecimal    toFormat = formatterOptions.alwaysPositive ? amount.abs() : amount;
      StringBuilder builder = new StringBuilder();
      NumberFormat  numberFormat;

      if (formatterOptions.withUnit) {
        numberFormat = NumberFormat.getCurrencyInstance(formatterOptions.locale);
        numberFormat.setCurrency(javaCurrency);
      } else {
        numberFormat = NumberFormat.getNumberInstance(formatterOptions.locale);
        numberFormat.setMinimumFractionDigits(javaCurrency.getDefaultFractionDigits());
      }

      numberFormat.setMaximumFractionDigits(Math.min(javaCurrency.getDefaultFractionDigits(),
                                                     formatterOptions.maximumFractionDigits));

      builder.append(numberFormat.format(toFormat));

      return builder.toString();
    }
  }

  private static final class CryptoFormatter extends Formatter {

    private final Currency currency;

    private CryptoFormatter(Currency currency, FormatterOptions formatterOptions) {
      super(formatterOptions);

      this.currency = currency;
    }

    @Override
    public String format(BigDecimal amount) {
      NumberFormat  format   = NumberFormat.getNumberInstance(formatterOptions.locale);
      BigDecimal    toFormat = formatterOptions.alwaysPositive ? amount.abs() : amount;
      StringBuilder builder  = new StringBuilder();

      format.setMaximumFractionDigits(Math.min(currency.getDecimalPrecision(), formatterOptions.maximumFractionDigits));

      if (toFormat.signum() == 1 && formatterOptions.alwaysPrefixWithSign) {
        builder.append("+");
      }

      builder.append(format.format(toFormat));

      if (formatterOptions.withSpaceBeforeUnit && formatterOptions.withUnit) {
        builder.append(" ");
      }

      if (formatterOptions.withUnit) {
        builder.append(currency.getCurrencyCode());
      }

      return builder.toString();
    }
  }
}
