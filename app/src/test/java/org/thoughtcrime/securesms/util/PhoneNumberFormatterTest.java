package org.thoughtcrime.securesms.util;

import junit.framework.AssertionFailedError;

import org.junit.Test;
import org.thoughtcrime.securesms.BaseUnitTest;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import static org.assertj.core.api.Assertions.assertThat;

public class PhoneNumberFormatterTest extends BaseUnitTest {
  private static final String LOCAL_NUMBER_US  = "+15555555555";
  private static final String NUMBER_CH        = "+41446681800";
  private static final String NUMBER_UK        = "+442079460018";
  private static final String NUMBER_DE        = "+4930123456";
  private static final String NUMBER_MOBILE_DE = "+49171123456";
  private static final String COUNTRY_CODE_CH  = "41";
  private static final String COUNTRY_CODE_UK  = "44";
  private static final String COUNTRY_CODE_DE  = "49";

  @Test
  public void testFormatNumber() throws Exception, InvalidNumberException {
    assertThat(PhoneNumberFormatter.formatNumber("(555) 555-5555", LOCAL_NUMBER_US)).isEqualTo(LOCAL_NUMBER_US);
    assertThat(PhoneNumberFormatter.formatNumber("555-5555", LOCAL_NUMBER_US)).isEqualTo(LOCAL_NUMBER_US);
    assertThat(PhoneNumberFormatter.formatNumber("(123) 555-5555", LOCAL_NUMBER_US)).isNotEqualTo(LOCAL_NUMBER_US);
  }

  @Test
  public void testFormatNumberEmail() throws Exception {
    try {
      PhoneNumberFormatter.formatNumber("person@domain.com", LOCAL_NUMBER_US);
      throw new AssertionFailedError("should have thrown on email");
    } catch (InvalidNumberException ine) {
      // success
    }
  }

  @Test
  public void testFormatNumberE164() throws Exception, InvalidNumberException {
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_UK, "(020) 7946 0018")).isEqualTo(NUMBER_UK);
//    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_UK, "044 20 7946 0018")).isEqualTo(NUMBER_UK);
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_UK, "+442079460018")).isEqualTo(NUMBER_UK);

    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_CH, "+41 44 668 18 00")).isEqualTo(NUMBER_CH);
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_CH, "+41 (044) 6681800")).isEqualTo(NUMBER_CH);

    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0049 030 123456")).isEqualTo(NUMBER_DE);
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0049 (0)30123456")).isEqualTo(NUMBER_DE);
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0049((0)30)123456")).isEqualTo(NUMBER_DE);
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "+49 (0) 30  1 2  3 45 6 ")).isEqualTo(NUMBER_DE);
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "030 123456")).isEqualTo(NUMBER_DE);

    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0171123456")).isEqualTo(NUMBER_MOBILE_DE);
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0171/123456")).isEqualTo(NUMBER_MOBILE_DE);
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "+490171/123456")).isEqualTo(NUMBER_MOBILE_DE);
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "00490171/123456")).isEqualTo(NUMBER_MOBILE_DE);
    assertThat(PhoneNumberFormatter.formatE164(COUNTRY_CODE_DE, "0049171/123456")).isEqualTo(NUMBER_MOBILE_DE);
  }

  @Test
  public void testFormatRemoteNumberE164() throws Exception, InvalidNumberException {
    assertThat(PhoneNumberFormatter.formatNumber("+4402079460018", LOCAL_NUMBER_US)).isEqualTo(NUMBER_UK);
  }


}
