package org.thoughtcrime.securesms.payments.currency;

import org.junit.Test;

import java.util.Currency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class CurrencyUtilTest_getCurrencyByE164 {

  @Test
  public void get_gbp_from_uk_number() {
    String e164 = "+441617151234";

    Currency currency = CurrencyUtil.getCurrencyByE164(e164);

    assertNotNull(currency);
    assertEquals("GBP", currency.getCurrencyCode());
  }

  @Test
  public void get_euros_from_german_number() {
    String e164 = "+4915223433333";

    Currency currency = CurrencyUtil.getCurrencyByE164(e164);

    assertNotNull(currency);
    assertEquals("EUR", currency.getCurrencyCode());
  }

  @Test
  public void get_usd_from_us_number() {
    String e164 = "+15407011234";

    Currency currency = CurrencyUtil.getCurrencyByE164(e164);

    assertNotNull(currency);
    assertEquals("USD", currency.getCurrencyCode());
  }

  @Test
  public void get_cad_from_canadian_number() {
    String e164 = "+15064971234";

    Currency currency = CurrencyUtil.getCurrencyByE164(e164);

    assertNotNull(currency);
    assertEquals("CAD", currency.getCurrencyCode());
  }
}
