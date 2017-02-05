package org.thoughtcrime.securesms.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.thoughtcrime.securesms.BaseUnitTest;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@PrepareForTest({ PhoneNumberUtil.class })
public class NumberUtilTest extends BaseUnitTest {
  private String[][] numbersToTest = new String[][] {
    { "+15555555555", "+*********55" },
    { "+41446681800", "+*********00" },
    { "+442079460018", "+**********18" },
    { "+4930123456", "+********56" },
    { "+49171123456", "+*********56" },
    { "+358041234567", "+**********67" } // Finnish mobile number
  };

  @Test public void testAnonymizePhoneNumber() throws Exception {
    for (String[] numbers : numbersToTest) {
        assertEquals(numbers[1], NumberUtil.anonymizePhoneNumber(numbers[0]));
    }
  }
}
