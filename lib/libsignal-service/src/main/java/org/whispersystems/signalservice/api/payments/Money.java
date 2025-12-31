package org.whispersystems.signalservice.api.payments;

import org.whispersystems.signalservice.api.util.Uint64RangeException;
import org.whispersystems.signalservice.api.util.Uint64Util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

public abstract class Money {

  /**
   * @param amount Can be positive or negative. Can exceed 64 bits.
   *               Must not have a decimal scale beyond that which mobile coin allows.
   */
  public static MobileCoin mobileCoin(BigDecimal amount) {
    return picoMobileCoin(amount.movePointRight(MobileCoin.PRECISION).toBigIntegerExact());
  }

  /**
   * @param picoMobileCoin Can be positive or negative. Can exceed 64 bits.
   */
  public static MobileCoin picoMobileCoin(BigInteger picoMobileCoin) {
    return picoMobileCoin.signum() == 0 ? MobileCoin.ZERO
                                        : new MobileCoin(picoMobileCoin);
  }

  /**
   * @param picoMobileCoinUint64 Treated as unsigned.
   */
  public static MobileCoin picoMobileCoin(long picoMobileCoinUint64) {
    return picoMobileCoin(Uint64Util.uint64ToBigInteger(picoMobileCoinUint64));
  }

  /**
   * Parses the output of {@link #serialize()}.
   *
   * @throws ParseException iff the format is incorrect.
   */
  public static Money parse(String serialized) throws ParseException {
    if (serialized == null) {
      throw new ParseException();
    }
    String[] split = serialized.split(":");
    if (split.length != 2) {
      throw new ParseException();
    }
    if (Money.MobileCoin.CURRENCY.getCurrencyCode().equals(split[0])) {
      return picoMobileCoin(new BigInteger(split[1]));
    }
    throw new ParseException();
  }

  /**
   * Parses the output of {@link #serialize()}. Asserts that there is no parsing exception.
   */
  public static Money parseOrThrow(String serialized) {
    try {
      return parse(serialized);
    } catch (ParseException e) {
      throw new AssertionError(e);
    }
  }

  public abstract boolean isPositive();

  public abstract boolean isNegative();

  public abstract boolean isEqualOrLessThanZero();

  public abstract Money negate();

  public abstract Money abs();

  public abstract Money add(Money other);

  public abstract Money subtract(Money other);

  public abstract Currency getCurrency();

  public abstract String serializeAmountString();

  public MobileCoin requireMobileCoin() {
    throw new AssertionError();
  }

  public final String serialize() {
    return getCurrency().getCurrencyCode() + ":" + serializeAmountString();
  }

  /**
   * Given instance of one money type, this will give you the corresponding zero value.
   */
  public abstract Money toZero();

  public static final class MobileCoin extends Money {
    public static final Comparator<MobileCoin> ASCENDING  = (x, y) -> x.amount.compareTo(y.amount);
    public static final Comparator<MobileCoin> DESCENDING = (x, y) -> y.amount.compareTo(x.amount);

    public static final MobileCoin ZERO = new MobileCoin(BigInteger.ZERO);
    public static final MobileCoin MAX_VALUE;

    static {
      byte[] bytes = new byte[8];
      Arrays.fill(bytes, (byte) 0xff);
      BigInteger max64Bit = new BigInteger(1, bytes);
      MAX_VALUE = Money.picoMobileCoin(max64Bit);
    }

    private static final int PRECISION = 12;

    public static final Currency CURRENCY  = Currency.fromCodeAndPrecision("MOB", PRECISION);

    private final BigInteger amount;
    private final BigDecimal amountDecimal;

    private MobileCoin(BigInteger amount) {
      this.amount        = amount;
      this.amountDecimal = new BigDecimal(amount).movePointLeft(PRECISION).stripTrailingZeros();
    }

    public static MobileCoin sum(Collection<MobileCoin> values) {
      switch (values.size()) {
        case 0:
          return ZERO;
        case 1:
          return values.iterator().next();
        default: {
          BigInteger result = ZERO.amount;

          for (MobileCoin value : values) {
            result = result.add(value.amount);
          }

          return Money.picoMobileCoin(result);
        }
      }
    }

    @Override
    public boolean isPositive() {
      return amount.signum() == 1;
    }

    @Override
    public boolean isNegative() {
      return amount.signum() == -1;
    }

    @Override
    public boolean isEqualOrLessThanZero() {
      return amount != null && amount.compareTo(BigInteger.ZERO) <= 0;
    }

    @Override
    public MobileCoin negate() {
      return new MobileCoin(amount.negate());
    }

    @Override
    public MobileCoin abs() {
      if (amount.signum() == -1) {
        return negate();
      }
      return this;
    }

    @Override
    public Money add(Money other) {
      return new MobileCoin(amount.add(other.requireMobileCoin().amount));
    }

    @Override
    public Money subtract(Money other) {
      return new MobileCoin(amount.subtract(other.requireMobileCoin().amount));
    }

    @Override
    public Currency getCurrency() {
      return CURRENCY;
    }

    @Override
    public MobileCoin requireMobileCoin() {
      return this;
    }

    @Override
    public Money toZero() {
      return ZERO;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof MobileCoin && amount.equals(((MobileCoin) o).amount);
    }

    @Override
    public int hashCode() {
      return amount.hashCode();
    }

    @Override
    public String serializeAmountString() {
      return toPicoMobBigInteger().toString();
    }

    /**
     * The value expressed in Mobile coin.
     */
    public String getAmountDecimalString() {
      return amountDecimal.toString();
    }

    public boolean greaterThan(MobileCoin other) {
      return amount.compareTo(other.amount) > 0;
    }

    public boolean lessThan(MobileCoin other) {
      return amount.compareTo(other.amount) < 0;
    }

    @Deprecated
    public double toDouble() {
      return amountDecimal.doubleValue();
    }

    public BigDecimal toBigDecimal() {
      return amountDecimal;
    }

    public BigInteger toPicoMobBigInteger() {
      return amount;
    }

    public long toPicoMobUint64() throws Uint64RangeException {
      return Uint64Util.bigIntegerToUInt64(amount);
    }

    @Override
    public String toString(Formatter formatter) {
      return formatter.format(amountDecimal);
    }
  }

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    return serialize();
  }

  public abstract String toString(Formatter formatter);

  public final String toString(FormatterOptions formatterOptions){
    Formatter formatter = getCurrency().getFormatter(formatterOptions);
    return toString(formatter);
  }

  public static final class ParseException extends Exception {
    private ParseException() {
    }
  }
}
