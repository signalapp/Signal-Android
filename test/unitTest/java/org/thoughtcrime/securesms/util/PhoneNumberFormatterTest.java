package org.thoughtcrime.securesms.util;

import junit.framework.AssertionFailedError;

import org.junit.Test;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;

import static org.assertj.core.api.Assertions.assertThat;

public class PhoneNumberFormatterTest {
  private static final String LOCAL_NUMBER = "+15555555555";

  @Test public void testFormatNumberE164() throws Exception, InvalidNumberException {
    assertThat(PhoneNumberFormatter.formatNumber("(555) 555-5555", LOCAL_NUMBER)).isEqualTo(LOCAL_NUMBER);
    assertThat(PhoneNumberFormatter.formatNumber("555-5555", LOCAL_NUMBER)).isEqualTo(LOCAL_NUMBER);
    assertThat(PhoneNumberFormatter.formatNumber("(123) 555-5555", LOCAL_NUMBER)).isNotEqualTo(LOCAL_NUMBER);
  }

  @Test public void testFormatNumberEmail() throws Exception {
    try {
      PhoneNumberFormatter.formatNumber("person@domain.com", LOCAL_NUMBER);
      throw new AssertionFailedError("should have thrown on email");
    } catch (InvalidNumberException ine) {
      // success
    }
  }
}
