package org.thoughtcrime.securesms.service;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Test;
import org.thoughtcrime.securesms.BaseUnitTest;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.when;

public class SmsListenerTest extends BaseUnitTest {
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
  }};

  private SmsListener listener;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    listener = new SmsListener();
    when(sharedPreferences.getBoolean(contains("pref_verifying"), anyBoolean())).thenReturn(true);
  }

  @Test
  public void testChallenges() throws Exception {
    for (Entry<String,String> challenge : CHALLENGES.entrySet()) {
      if (!listener.isChallenge(context, challenge.getKey())) {
        throw new AssertionFailedError("SmsListener didn't recognize body as a challenge.");
      }
      assertEquals(listener.parseChallenge(challenge.getKey()), challenge.getValue());
    }
  }
}
