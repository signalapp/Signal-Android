package org.thoughtcrime.securesms.service;

import org.junit.Before;
import org.junit.Test;
import org.thoughtcrime.securesms.BaseUnitTest;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.when;

public class VerificationCodeParserTest extends BaseUnitTest {

  public static Collection<String[]> challenges() {
    return Arrays.asList(new String[][]{
        {"Your TextSecure verification code: 337-337", "337337"},
        {"XXX\nYour TextSecure verification code: 1337-1337", "13371337"},
        {"Your TextSecure verification code: 337-1337", "3371337"},
        {"Your TextSecure verification code: 1337-337", "1337337"},
        {"Your TextSecure verification code: 1337-1337", "13371337"},
        {"XXXYour TextSecure verification code: 1337-1337", "13371337"},
        {"Your TextSecure verification code: 1337-1337XXX", "13371337"},
        {"Your TextSecure verification code 1337-1337", "13371337"},

        {"Your Signal verification code: 337-337", "337337"},
        {"XXX\nYour Signal verification code: 1337-1337", "13371337"},
        {"Your Signal verification code: 337-1337", "3371337"},
        {"Your Signal verification code: 1337-337", "1337337"},
        {"Your Signal verification code: 1337-1337", "13371337"},
        {"XXXYour Signal verification code: 1337-1337", "13371337"},
        {"Your Signal verification code: 1337-1337XXX", "13371337"},
        {"Your Signal verification code 1337-1337", "13371337"},

        {"<#>Your Signal verification code: 1337-1337 aAbBcCdDeEf", "13371337"},
        {"<#> Your Signal verification code: 1337-1337 aAbBcCdDeEf", "13371337"},
        {"<#>Your Signal verification code: 1337-1337\naAbBcCdDeEf", "13371337"},
        {"<#> Your Signal verification code: 1337-1337\naAbBcCdDeEf", "13371337"},
        {"<#> Your Signal verification code: 1337-1337\n\naAbBcCdDeEf", "13371337"},

        {" 1234-5678", "12345678"},
        {"1234-5678", "12345678"},
        {">1234-5678 is your verification code.", "12345678"},
        {"1234-5678 is your verification code.", "12345678"},
        {"$1234-5678", "12345678"},
        {"hi 1234-5678\n\nsgnl://verify/1234-5678\n\naAbBcCdDeEf", "12345678"},
        {"howdy 1234-5678\n\nsgnl://verify/1234-5678\n\naAbBcCdDeEf", "12345678"},
        {"test 1234-5678\n\nsgnl://verify/1234-5678", "12345678"},
        {"%#($#&@**$@(@*1234-5678\naAbBcCdDeEf", "12345678"}
    });
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    when(sharedPreferences.getBoolean(contains("pref_verifying"), anyBoolean())).thenReturn(true);
  }

  @Test
  public void testChallenges() {
    for (String[] challenge : challenges()) {
      Optional<String> result = VerificationCodeParser.parse(challenge[0]);
      assertTrue(result.isPresent());
      assertEquals(challenge[1], result.get());
    }
  }
}
