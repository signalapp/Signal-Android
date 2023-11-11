package org.whispersystems.signalservice.api.payments;

import org.junit.Test;
import org.whispersystems.signalservice.api.util.Uint64RangeException;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class MoneyTest_MobileCoin {

  @Test
  public void create_zero() {
    Money mobileCoin = Money.mobileCoin(BigDecimal.ZERO);

    assertFalse(mobileCoin.isPositive());
    assertFalse(mobileCoin.isNegative());
  }

  @Test
  public void create_positive() {
    Money mobileCoin = Money.mobileCoin(BigDecimal.ONE);

    assertTrue(mobileCoin.isPositive());
    assertFalse(mobileCoin.isNegative());
  }

  @Test
  public void create_negative() {
    Money mobileCoin = Money.mobileCoin(BigDecimal.ONE.negate());

    assertFalse(mobileCoin.isPositive());
    assertTrue(mobileCoin.isNegative());
  }

  @Test
  public void toString_format() {
    Money.MobileCoin mobileCoin = Money.mobileCoin(BigDecimal.valueOf(-1000.32456));

    assertEquals("MOB:-1000324560000000", mobileCoin.toString());
  }

  @Test
  public void toAmountString_format() {
    Money.MobileCoin mobileCoin = Money.mobileCoin(BigDecimal.valueOf(-1000.32456));

    assertEquals("-1000.32456", mobileCoin.getAmountDecimalString());
  }

  @Test
  public void currency() {
    Money mobileCoin = Money.mobileCoin(BigDecimal.valueOf(-1000.32456));

    assertEquals("MOB", mobileCoin.getCurrency().getCurrencyCode());
    assertEquals(12, mobileCoin.getCurrency().getDecimalPrecision());
  }

  @Test
  public void equality() {
    Money mobileCoin1  = Money.mobileCoin(BigDecimal.ONE);
    Money mobileCoin10 = Money.mobileCoin(BigDecimal.ONE);

    assertEquals(mobileCoin1, mobileCoin10);
    assertEquals(mobileCoin1.hashCode(), mobileCoin10.hashCode());
  }

  @Test
  public void inequality() {
    Money mobileCoin1  = Money.mobileCoin(BigDecimal.ONE);
    Money mobileCoin10 = Money.mobileCoin(BigDecimal.TEN);

    assertNotEquals(mobileCoin1, mobileCoin10);
    assertNotEquals(mobileCoin1.hashCode(), mobileCoin10.hashCode());
  }

  @Test
  public void negate() {
    Money money1         = Money.mobileCoin(BigDecimal.ONE);
    Money moneyNegative1 = Money.mobileCoin(BigDecimal.ONE.negate());
    Money negated        = money1.negate();

    assertEquals(moneyNegative1, negated);
  }

  @Test
  public void abs() {
    Money money0         = Money.mobileCoin(BigDecimal.ZERO);
    Money money1         = Money.mobileCoin(BigDecimal.ONE);
    Money moneyNegative1 = Money.mobileCoin(BigDecimal.ONE.negate());
    Money absOfZero      = money0.abs();
    Money absOfPositive  = money1.abs();
    Money absOfNegative  = moneyNegative1.abs();

    assertSame(money0, absOfZero);
    assertSame(money1, absOfPositive);
    assertEquals(money1, absOfNegative);
  }

  @Test
  public void require_cast() {
    Money            money      = Money.mobileCoin(BigDecimal.ONE.negate());
    Money.MobileCoin mobileCoin = money.requireMobileCoin();

    assertSame(money, mobileCoin);
  }

  @Test
  public void serialize_negative() {
    Money.MobileCoin mobileCoin = Money.mobileCoin(BigDecimal.valueOf(-1000.32456));

    assertEquals("MOB:-1000324560000000", mobileCoin.serialize());
  }

  @Test
  public void parse_negative() throws Money.ParseException {
    Money  original   = Money.mobileCoin(BigDecimal.valueOf(-1000.32456));
    String serialized = original.serialize();

    Money deserialized = Money.parse(serialized);

    assertEquals(original, deserialized);
  }

  @Test
  public void parseOrThrow() {
    Money  original   = Money.mobileCoin(BigDecimal.valueOf(-123.6323));
    String serialized = original.serialize();

    Money deserialized = Money.parseOrThrow(serialized);

    assertEquals(original, deserialized);
  }

  @Test
  public void parse_zero() {
    Money value = Money.parseOrThrow("MOB:0000000000000000");

    assertSame(Money.MobileCoin.ZERO, value);
  }

  @Test(expected = Money.ParseException.class)
  public void parse_fail_empty() throws Money.ParseException {
    Money.parse("");
  }

  @Test(expected = Money.ParseException.class)
  public void parse_fail_null() throws Money.ParseException {
    Money.parse(null);
  }

  @Test(expected = Money.ParseException.class)
  public void parse_fail_unknown_currency() throws Money.ParseException {
    Money.parse("XYZ:123");
  }

  @Test(expected = Money.ParseException.class)
  public void parse_fail_no_value() throws Money.ParseException {
    Money.parse("MOB");
  }

  @Test(expected = Money.ParseException.class)
  public void parse_fail_too_many_parts() throws Money.ParseException {
    Money.parse("MOB:1:2");
  }
  
  @Test(expected = AssertionError.class)
  public void parseOrThrowOrThrow_fail_empty() {
    Money.parseOrThrow("");
  }

  @Test(expected = AssertionError.class)
  public void parseOrThrowOrThrow_fail_null() {
    Money.parseOrThrow(null);
  }

  @Test(expected = AssertionError.class)
  public void parseOrThrowOrThrow_fail_unknown_currency() {
    Money.parseOrThrow("XYZ:123");
  }

  @Test(expected = AssertionError.class)
  public void parseOrThrowOrThrow_fail_no_value() {
    Money.parseOrThrow("MOB");
  }

  @Test(expected = AssertionError.class)
  public void parseOrThrowOrThrow_fail_too_many_parts() {
    Money.parseOrThrow("MOB:1:2");
  }

  @Test
  public void from_big_integer_picoMobileCoin() {
    Money.MobileCoin mobileCoin1 = Money.mobileCoin(new BigDecimal("352324.325232123456"));
    Money.MobileCoin mobileCoin2 = Money.picoMobileCoin(BigInteger.valueOf(352324325232123456L));

    assertEquals(mobileCoin1, mobileCoin2);
  }

  @Test
  public void from_big_integer_picoMobileCoin_zero() {
    Money.MobileCoin mobileCoin = Money.picoMobileCoin(BigInteger.ZERO);

    assertSame(Money.MobileCoin.ZERO, mobileCoin);
  }

  @Test
  public void from_very_large_big_integer_picoMobileCoin() {
    Money.MobileCoin mobileCoin1 = Money.mobileCoin(new BigDecimal("352324.325232123456"));
    Money.MobileCoin mobileCoin2 = Money.picoMobileCoin(BigInteger.valueOf(352324325232123456L));

    assertEquals(mobileCoin1, mobileCoin2);
  }

  @Test
  public void to_picoMob_bigInteger() {
    Money.MobileCoin mobileCoin1 = Money.mobileCoin(BigDecimal.valueOf(21324.325232));

    BigInteger bigInteger = mobileCoin1.toPicoMobBigInteger();

    assertEquals(BigInteger.valueOf(21324325232000000L), bigInteger);
  }

  @Test(expected = ArithmeticException.class)
  public void precision_loss_on_creation() {
    Money.mobileCoin(new BigDecimal("10376293.0000000000001"));
  }

  @Test(expected = ArithmeticException.class)
  public void precision_loss_on_creation_negative() {
    Money.mobileCoin(new BigDecimal("-10376293.0000000000001"));
  }

  @Test
  public void from_picoMob() {
    Money.MobileCoin mobileCoin1 = Money.picoMobileCoin(1234567890987654321L);

    assertEquals("1234567.890987654321", mobileCoin1.getAmountDecimalString());
  }

  @Test
  public void to_picoMob() throws Uint64RangeException {
    Money.MobileCoin mobileCoin1 = Money.picoMobileCoin(1234567890987654321L);

    assertEquals(1234567890987654321L, mobileCoin1.toPicoMobUint64());
  }

  @Test
  public void from_large_picoMob() {
    Money.MobileCoin mobileCoin1 = Money.picoMobileCoin(0x9000000000000000L);

    assertEquals("10376293.541461622784", mobileCoin1.getAmountDecimalString());
  }

  @Test
  public void from_large_negative() {
    Money.MobileCoin mobileCoin1 = Money.mobileCoin(new BigDecimal("-1234567.890987654321"));

    assertEquals("-1234567.890987654321", mobileCoin1.getAmountDecimalString());
  }

  @Test
  public void to_large_picoMob() throws Uint64RangeException {
    Money.MobileCoin mobileCoin1 = Money.picoMobileCoin(0x9000000000000000L);

    assertEquals(0x9000000000000000L, mobileCoin1.toPicoMobUint64());
  }

  @Test
  public void from_maximum_picoMob() {
    Money.MobileCoin mobileCoin = Money.picoMobileCoin(0xffffffffffffffffL);

    assertEquals("18446744073709551615", mobileCoin.serializeAmountString());
  }

  @Test
  public void from_maximum_picoMob_and_back() throws Uint64RangeException {
    Money.MobileCoin mobileCoin = Money.picoMobileCoin(0xffffffffffffffffL);

    assertEquals(0xffffffffffffffffL, mobileCoin.toPicoMobUint64());
  }

  @Test
  public void large_mobile_coin_value_exceeding_64_bits() {
    Money.MobileCoin mobileCoin = Money.mobileCoin(new BigDecimal("18446744.073709551616"));

    assertEquals("18446744073709551616", mobileCoin.serializeAmountString());
  }

  @Test(expected = Uint64RangeException.class)
  public void large_mobile_coin_value_exceeding_64_bits_toPicoMobUint64_failure() throws Uint64RangeException {
    Money.MobileCoin mobileCoin = Money.mobileCoin(new BigDecimal("18446744.073709551616"));

    mobileCoin.toPicoMobUint64();
  }

  @Test(expected = Uint64RangeException.class)
  public void negative_to_pico_mob_uint64() throws Uint64RangeException {
    Money.MobileCoin mobileCoin1 = Money.mobileCoin(new BigDecimal("-1"));

    mobileCoin1.toPicoMobUint64();
  }

  @Test
  public void greater_than() {
    assertTrue(mobileCoin2(2).greaterThan(mobileCoin2(1)));
    assertTrue(mobileCoin2(-1).greaterThan(mobileCoin2(-2)));
    assertFalse(mobileCoin2(2).greaterThan(mobileCoin2(2)));
    assertFalse(mobileCoin2(1).greaterThan(mobileCoin2(2)));
  }

  @Test
  public void less_than() {
    assertTrue(mobileCoin2(1).lessThan(mobileCoin2(2)));
    assertTrue(mobileCoin2(-2).lessThan(mobileCoin2(-1)));
    assertFalse(mobileCoin2(2).lessThan(mobileCoin2(2)));
    assertFalse(mobileCoin2(2).lessThan(mobileCoin2(1)));
  }

  @Test
  public void zero_constant() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ZERO);
    Money mobileCoin2 = Money.MobileCoin.ZERO;

    assertEquals(mobileCoin1, mobileCoin2);
  }

  @Test
  public void to_zero() {
    Money mobileCoin = Money.mobileCoin(BigDecimal.ONE);

    assertSame(Money.MobileCoin.ZERO, mobileCoin.toZero());
  }

  @Test
  public void max_long_value() {
    assertEquals("MOB:18446744073709551615", Money.MobileCoin.MAX_VALUE.serialize());
  }

  private static Money.MobileCoin mobileCoin2(double value) {
    return Money.mobileCoin(BigDecimal.valueOf(value));
  }
}
