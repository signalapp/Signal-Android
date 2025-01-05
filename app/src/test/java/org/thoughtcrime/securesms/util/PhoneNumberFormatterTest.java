package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

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
    assertEquals(NUMBER_UK, PhoneNumberFormatter.formatNumber("+4402079460018", LOCAL_NUMBER_US));
  }
}
