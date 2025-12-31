package org.whispersystems.signalservice.api.payments;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public final class MoneyTest_MobileCoin_sum {

  @Test
  public void sum_empty_list() {
    Money sum = Money.MobileCoin.sum(emptyList());

    assertSame(Money.MobileCoin.ZERO, sum);
  }

  @Test
  public void sum_1() {
    Money.MobileCoin mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);

    Money sum = Money.MobileCoin.sum(singletonList(mobileCoin1));

    assertSame(mobileCoin1, sum);
  }

  @Test
  public void sum_2() {
    Money.MobileCoin mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);
    Money.MobileCoin mobileCoin2 = Money.mobileCoin(BigDecimal.valueOf(2));

    Money sum = Money.MobileCoin.sum(asList(mobileCoin1, mobileCoin2));

    assertEquals(Money.mobileCoin(BigDecimal.valueOf(3)), sum);
  }

  @Test
  public void sum_negatives() {
    Money.MobileCoin mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);
    Money.MobileCoin mobileCoin2 = Money.mobileCoin(BigDecimal.valueOf(-2));

    Money sum = Money.MobileCoin.sum(asList(mobileCoin1, mobileCoin2));

    assertEquals(Money.mobileCoin(BigDecimal.valueOf(-1)), sum);
  }
}
