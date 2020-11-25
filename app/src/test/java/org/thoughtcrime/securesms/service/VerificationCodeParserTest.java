package org.thoughtcrime.securesms.service;

import org.junit.Before;
import org.junit.Test;
import org.thoughtcrime.securesms.BaseUnitTest;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.when;

public class VerificationCodeParserTest extends BaseUnitTest {
  private static Map<String, String> CHALLENGES = new HashMap<String,String>() {{
      put("Your TextSecure verification code: 337-337",        "337337");
      put("XXX\nYour TextSecure verification code: 1337-1337", "13371337");
      put("Your TextSecure verification code: 337-1337",       "3371337");
      put("Your TextSecure verification code: 1337-337",       "1337337");
      put("Your TextSecure verification code: 1337-1337",      "13371337");
      put("XXXYour TextSecure verification code: 1337-1337",   "13371337");
      put("Your TextSecure verification code: 1337-1337XXX",   "13371337");
      put("Your TextSecure verification code 1337-1337",       "13371337");

      put("Your Signal verification code: 337-337",        "337337");
      put("XXX\nYour Signal verification code: 1337-1337", "13371337");
      put("Your Signal verification code: 337-1337",       "3371337");
      put("Your Signal verification code: 1337-337",       "1337337");
      put("Your Signal verification code: 1337-1337",      "13371337");
      put("XXXYour Signal verification code: 1337-1337",   "13371337");
      put("Your Signal verification code: 1337-1337XXX",   "13371337");
      put("Your Signal verification code 1337-1337",       "13371337");

      put("<#>Your Signal verification code: 1337-1337 aAbBcCdDeEf",     "13371337");
      put("<#> Your Signal verification code: 1337-1337 aAbBcCdDeEf",    "13371337");
      put("<#>Your Signal verification code: 1337-1337\naAbBcCdDeEf",    "13371337");
      put("<#> Your Signal verification code: 1337-1337\naAbBcCdDeEf",   "13371337");
      put("<#> Your Signal verification code: 1337-1337\n\naAbBcCdDeEf", "13371337");
  }};

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    when(sharedPreferences.getBoolean(contains("pref_verifying"), anyBoolean())).thenReturn(true);
  }

  @Test
  public void testChallenges() {
    for (Entry<String,String> challenge : CHALLENGES.entrySet()) {
      Optional<String> result = VerificationCodeParser.parse(context, challenge.getKey());

      assertTrue(result.isPresent());
      assertEquals(result.get(), challenge.getValue());
    }
  }
}
