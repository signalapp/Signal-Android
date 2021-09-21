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

public final class MoneyTest_MobileCoin_add {

  @Test
  public void add_0() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ZERO);
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.ZERO);

    Money sum = mobileCoin1.add(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.ZERO), sum);
  }

  @Test
  public void add_1_rhs() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ZERO);
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.ONE);

    Money sum = mobileCoin1.add(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.ONE), sum);
  }

  @Test
  public void add_1_lhs() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.ZERO);

    Money sum = mobileCoin1.add(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.ONE), sum);
  }

  @Test
  public void add_2() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.ONE);

    Money sum = mobileCoin1.add(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.valueOf(2)), sum);
  }

  @Test
  public void add_fraction() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.valueOf(2.2));

    Money sum = mobileCoin1.add(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.valueOf(3.2)), sum);
  }

  @Test
  public void add_negative_fraction() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.valueOf(-5.2));
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.ONE);

    Money sum = mobileCoin1.add(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.valueOf(-4.2)), sum);
  }
}
