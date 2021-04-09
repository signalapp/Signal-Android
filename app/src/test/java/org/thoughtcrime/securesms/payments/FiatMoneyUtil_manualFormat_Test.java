package org.thoughtcrime.securesms.payments;

import org.junit.Test;

import java.util.Currency;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

public final class FiatMoneyUtil_manualFormat_Test {

  @Test
  public void gbp_UK() {
    Locale.setDefault(Locale.UK);

    String format = FiatMoneyUtil.manualFormat(Currency.getInstance("GBP"), "1.20");

    assertEquals("£1.20", format);
  }

  @Test
  public void eur_France() {
    Locale.setDefault(Locale.FRANCE);

    String format = FiatMoneyUtil.manualFormat(Currency.getInstance("EUR"), "2");

    assertEquals("2 €", format);
  }

  @Test
  public void aud_France() {
    Locale.setDefault(Locale.FRANCE);

    String format = FiatMoneyUtil.manualFormat(Currency.getInstance("AUD"), "1");

    assertEquals("1 AUD", format);
  }

  @Test
  public void usd_US() {
    Locale.setDefault(Locale.US);

    String format = FiatMoneyUtil.manualFormat(Currency.getInstance("USD"), "4.0");

    assertEquals("$4.0", format);
  }

  @Test
  public void cad_US() {
    Locale.setDefault(Locale.US);

    String format = FiatMoneyUtil.manualFormat(Currency.getInstance("CAD"), "5.00");

    assertEquals("CAD5.00", format);
  }

  @Test
  public void cad_Canada() {
    Locale.setDefault(Locale.CANADA);

    String format = FiatMoneyUtil.manualFormat(Currency.getInstance("CAD"), "5.12");

    assertEquals("$5.12", format);
  }
}
