package org.whispersystems.signalservice.api.payments;

public abstract class Currency {

  private Currency() {}

  public abstract String getCurrencyCode();
  public abstract int getDecimalPrecision();
  public abstract Formatter getFormatter(FormatterOptions formatterOptions);

  private static class CryptoCurrency extends Currency {

    private final String currencyCode;
    private final int    decimalPrecision;

    CryptoCurrency(String currencyCode, int decimalPrecision) {
      this.currencyCode     = currencyCode;
      this.decimalPrecision = decimalPrecision;
    }

    public String getCurrencyCode() {
      return currencyCode;
    }

    public int getDecimalPrecision() {
      return decimalPrecision;
    }

    @Override
    public Formatter getFormatter(FormatterOptions formatterOptions) {
      return Formatter.forMoney(this, formatterOptions);
    }
  }

  private static class FiatCurrency extends Currency {

    private final java.util.Currency javaCurrency;

    private FiatCurrency(java.util.Currency javaCurrency) {
      this.javaCurrency = javaCurrency;
    }

    @Override
    public String getCurrencyCode() {
      return javaCurrency.getCurrencyCode();
    }

    @Override
    public int getDecimalPrecision() {
      return javaCurrency.getDefaultFractionDigits();
    }

    @Override
    public Formatter getFormatter(FormatterOptions formatterOptions) {
      return Formatter.forFiat(javaCurrency, formatterOptions);
    }
  }

  public static Currency fromJavaCurrency(java.util.Currency javaCurrency) {
    return new FiatCurrency(javaCurrency);
  }

  public static Currency fromCodeAndPrecision(String code, int decimalPrecision) {
    return new CryptoCurrency(code, decimalPrecision);
  }
}
