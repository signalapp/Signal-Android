package org.whispersystems.signalservice.api.payments;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static java.util.Arrays.asList;

public final class MoneyTest_MobileCoin_comparators {

  @Test
  public void sort_ascending() {
    Money.MobileCoin       mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);
    Money.MobileCoin       mobileCoin2 = Money.mobileCoin(BigDecimal.valueOf(2));
    List<Money.MobileCoin> list        = asList(mobileCoin2, mobileCoin1);
    list.sort(Money.MobileCoin.ASCENDING);

    assertEquals(asList(mobileCoin1, mobileCoin2), list);
  }

  @Test
  public void sort_descending() {
    Money.MobileCoin       mobileCoin1 = Money.mobileCoin(BigDecimal.ONE);
    Money.MobileCoin       mobileCoin2 = Money.mobileCoin(BigDecimal.valueOf(2));
    List<Money.MobileCoin> list        = asList(mobileCoin1, mobileCoin2);
    list.sort(Money.MobileCoin.DESCENDING);

    assertEquals(asList(mobileCoin2, mobileCoin1), list);
  }
}
