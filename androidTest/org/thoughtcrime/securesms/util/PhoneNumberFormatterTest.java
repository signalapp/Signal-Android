package org.thoughtcrime.securesms.util;

import android.test.AndroidTestCase;

import junit.framework.AssertionFailedError;

import org.whispersystems.textsecure.util.InvalidNumberException;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;
import static org.fest.assertions.api.Assertions.assertThat;

public class PhoneNumberFormatterTest extends AndroidTestCase {
  private static final String LOCAL_NUMBER = "+15555555555";

  public void testFormatNumberE164() throws Exception, InvalidNumberException {
    assertThat(PhoneNumberFormatter.formatNumber("(555) 555-5555", LOCAL_NUMBER)).isEqualTo(LOCAL_NUMBER);
    assertThat(PhoneNumberFormatter.formatNumber("555-5555", LOCAL_NUMBER)).isEqualTo(LOCAL_NUMBER);
    assertThat(PhoneNumberFormatter.formatNumber("(123) 555-5555", LOCAL_NUMBER)).isNotEqualTo(LOCAL_NUMBER);
  }

  public void testFormatNumberEmail() throws Exception {
    try {
      PhoneNumberFormatter.formatNumber("person@domain.com", LOCAL_NUMBER);
      throw new AssertionFailedError("should have thrown on email");
    } catch (InvalidNumberException ine) {
      // success
    }
  }

  public void testFormatNumberShortcodes() throws Exception, InvalidNumberException {
    assertThat(PhoneNumberFormatter.formatNumber("111111", LOCAL_NUMBER)).isEqualTo("111111");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }
}
