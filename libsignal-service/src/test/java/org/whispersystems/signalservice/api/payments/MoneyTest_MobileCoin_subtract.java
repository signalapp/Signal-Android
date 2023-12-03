package org.whispersystems.signalservice.api.payments;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public final class MoneyTest_MobileCoin_subtract {

  @Test
  public void subtract_0() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ZERO);
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.ZERO);

    Money sum = mobileCoin1.subtract(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.ZERO), sum);
  }

  @Test
  public void subtract_1_rhs() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ZERO);
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.ONE);

    Money sum = mobileCoin1.subtract(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.ONE.negate()), sum);
  }

  @Test
  public void subtract_1_lhs() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.ZERO);

    Money sum = mobileCoin1.subtract(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.ONE), sum);
  }

  @Test
  public void subtract_2() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.ONE);

    Money sum = mobileCoin1.subtract(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.ZERO), sum);
  }

  @Test
  public void subtract_fraction() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.valueOf(2.2));
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.ONE);

    Money sum = mobileCoin1.subtract(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.valueOf(1.2)), sum);
  }

  @Test
  public void subtract_negative_fraction() {
    Money mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);
    Money mobileCoin2 = Money.mobileCoin(BigDecimal.valueOf(-5.2));

    Money sum = mobileCoin1.subtract(mobileCoin2);

    assertEquals(Money.mobileCoin(BigDecimal.valueOf(6.2)), sum);
  }
}
