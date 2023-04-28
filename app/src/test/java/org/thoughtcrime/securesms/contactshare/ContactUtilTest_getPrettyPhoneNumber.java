package org.thoughtcrime.securesms.contactshare;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class ContactUtilTest_getPrettyPhoneNumber {

  private final Locale locale;
  private final String input;
  private final String expected;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{

      /* Already international */
      { Locale.US, "+15551234567", "+1 555-123-4567" },
      { Locale.US, "+44 7700900000", "+44 7700 900000" },

      /* US */
      { Locale.US, "555-123-4567", "+1 555-123-4567" },

      /* GB */
      { new Locale("en" ,"GB"), "07700900000", "+44 7700 900000" },

      /* Hungary */
      { new Locale("hu" ,"HU"), "0655153211", "+36 55 153 211" },

      /* Canaries is a region that does not have an ISO3 country code */
      { new Locale("es", "IC"), "+345551224116", "+34 5551224116" },

    });
  }

  public ContactUtilTest_getPrettyPhoneNumber(Locale locale, String input, String expected) {
    this.locale   = locale;
    this.input    = input;
    this.expected = expected;
  }

  @Test
  public void prettyPhoneNumber() {
    String phoneNumber = ContactUtil.getPrettyPhoneNumber(new Contact.Phone(input, Contact.Phone.Type.MOBILE, null), locale);

    assertEquals(expected, phoneNumber);
  }
}
