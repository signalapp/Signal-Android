package org.whispersystems.signalservice.api.payments;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

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
