package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.testutil.EmptyLogger;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class LocaleRemoteConfigTest_getCountryValue {

  private final String               phoneNumber;
  private final Map<String, Integer> countryCounts;
  private final long                 output;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
            {"+1 555 555 5555", new HashMap<String, Integer>() {{
              put("1", 10000);
              put("*", 400);
            }}, 10000},
            {"+1 555 555 5555", new HashMap<String, Integer>() {{
              put("011", 1000);
              put("1", 20000);
            }}, 20000},
            {"+1 555 555 5555", new HashMap<String, Integer>() {{
              put("011", 1000);
              put("a", 123);
              put("abba", 0);
            }}, 0},
            {"+1 555", new HashMap<String, Integer>() {{
              put("011", 1000);
              put("1", 1000);
            }}, 1000},
            {"+81 555 555 5555", new HashMap<String, Integer>() {{
              put("81", 6000);
              put("1", 1000);
              put("*", 2000);
            }}, 6000},
            {"+81 555 555 5555", new HashMap<String, Integer>() {{
              put("0011", 6000);
              put("1", 1000);
              put("*", 2000);
            }}, 2000},
            {"+49 555 555 5555", new HashMap<String, Integer>() {{
              put("0011", 6000);
              put("1", 1000);
              put("*", 2000);
            }}, 2000}
    });
  }

  @BeforeClass
  public static void setup() {
    Log.initialize(new EmptyLogger());
  }

  public LocaleRemoteConfigTest_getCountryValue(@NonNull String phoneNumber,
                                                @NonNull Map<String, Integer> countryCounts,
                                                long output)
  {
    this.phoneNumber   = phoneNumber;
    this.countryCounts = countryCounts;
    this.output        = output;
  }

  @Test
  public void determineCountEnabled() {
    assertEquals(output, LocaleRemoteConfig.getCountryValue(countryCounts, phoneNumber, 0));
  }

}
