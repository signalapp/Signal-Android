package org.whispersystems.signalservice.api.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PhoneNumberFormatterTest {
  private static final String LOCAL_NUMBER_US  = "+15555555555";
  private static final String NUMBER_CH        = "+41446681800";
  private static final String NUMBER_UK        = "+442079460018";
  private static final String NUMBER_DE        = "+4930123456";
  private static final String NUMBER_MOBILE_DE = "+49171123456";
  private static final String COUNTRY_CODE_CH  = "41";
  private static final String COUNTRY_CODE_UK  = "44";
  private static final String COUNTRY_CODE_DE  = "49";

  @Test
  public void testIsValidNumber() {
    assertTrue(PhoneNumberFormatter.isValidNumber("+6831234", "683"));
    assertTrue(PhoneNumberFormatter.isValidNumber("+35851234", "358"));
    assertTrue(PhoneNumberFormatter.isValidNumber("+358512345", "358"));

    assertTrue(PhoneNumberFormatter.isValidNumber("+5521912345678", "55"));
    assertTrue(PhoneNumberFormatter.isValidNumber("+552112345678", "55"));
    assertTrue(PhoneNumberFormatter.isValidNumber("+16105880522", "1"));

    assertFalse(PhoneNumberFormatter.isValidNumber("+014085041212", "0"));
    assertFalse(PhoneNumberFormatter.isValidNumber("+014085041212", "1"));
    assertFalse(PhoneNumberFormatter.isValidNumber("+5512345678", "55"));
    assertFalse(PhoneNumberFormatter.isValidNumber("+161058805220", "1"));
    assertFalse(PhoneNumberFormatter.isValidNumber("+1610588052", "1"));
    assertFalse(PhoneNumberFormatter.isValidNumber("+15880522", "1"));

    assertTrue(PhoneNumberFormatter.isValidNumber("+971812345678901", "971"));
    assertFalse(PhoneNumberFormatter.isValidNumber("+9718123456789012", "971"));
  }

  @Test
  public void testFormatNumber() throws InvalidNumberException {
    assertEquals(LOCAL_NUMBER_US, PhoneNumberFormatter.formatNumber("(555) 555-5555", LOCAL_NUMBER_US));
    assertEquals(LOCAL_NUMBER_US, PhoneNumberFormatter.formatNumber("555-5555", LOCAL_NUMBER_US));
    assertNotEquals(LOCAL_NUMBER_US, PhoneNumberFormatter.formatNumber("(123) 555-5555", LOCAL_NUMBER_US));
  }

  @Test
  public void testFormatNumberEmail() {
    assertThrows(
        "should have thrown on email",
        InvalidNumberException.class,
        () -> PhoneNumberFormatter.formatNumber("person@domain.com", LOCAL_NUMBER_US)
    );
  }

  @Test
  public void testFormatNumberE164() {
    assertEquals(NUMBER_UK, PhoneNumberFormatter.formatE164(COUNTRY_CODE_UK, "(020) 7946 0018"));
//    assertEquals(NUMBER_UK, PhoneNumberFormatter.formatE164(COUNTRY_CODE_UK, "044 20 7946 0018"));
    assertEquals(NUMBER_UK, PhoneNumberFormatter.formatE164(COUNTRY_CODE_UK, "+442079460018"));
    assertEquals(NUMBER_UK, PhoneNumberFormatter.formatE164(COUNTRY_CODE_UK, "+4402079460018"));

    assertEquals(NUMBER_CH, PhoneNumberFormatter.formatE164(COUNTRY_CODE_CH, "+41 44 668 18 00"));
    assertEquals(NUMBER_CH, PhoneNumberFormatter.formatE164(COUNTRY_CODE_CH, "+41 (044) 6681800"));

    assertEquals(NUMBER_DE, PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0049 030 123456"));
    assertEquals(NUMBER_DE, PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0049 (0)30123456"));
    assertEquals(NUMBER_DE, PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0049((0)30)123456"));
    assertEquals(NUMBER_DE, PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "+49 (0) 30  1 2  3 45 6 "));
    assertEquals(NUMBER_DE, PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "030 123456"));

    assertEquals(NUMBER_MOBILE_DE, PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0171123456"));
    assertEquals(NUMBER_MOBILE_DE, PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0171/123456"));
    assertEquals(NUMBER_MOBILE_DE, PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "+490171/123456"));
    assertEquals(NUMBER_MOBILE_DE, PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "00490171/123456"));
    assertEquals(NUMBER_MOBILE_DE, PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0049171/123456"));
  }

  @Test
  public void testFormatRemoteNumberE164() throws InvalidNumberException {
    assertEquals(LOCAL_NUMBER_US, PhoneNumberFormatter.formatNumber(LOCAL_NUMBER_US, NUMBER_UK));
    assertEquals(LOCAL_NUMBER_US, PhoneNumberFormatter.formatNumber(LOCAL_NUMBER_US, LOCAL_NUMBER_US));

    assertEquals(NUMBER_UK, PhoneNumberFormatter.formatNumber(NUMBER_UK, NUMBER_UK));
    assertEquals(NUMBER_CH, PhoneNumberFormatter.formatNumber(NUMBER_CH, NUMBER_CH));
    assertEquals(NUMBER_DE, PhoneNumberFormatter.formatNumber(NUMBER_DE, NUMBER_DE));
    assertEquals(NUMBER_MOBILE_DE, PhoneNumberFormatter.formatNumber(NUMBER_MOBILE_DE, NUMBER_DE));

    assertEquals(NUMBER_UK, PhoneNumberFormatter.formatNumber("+4402079460018", LOCAL_NUMBER_US));
  }
}
