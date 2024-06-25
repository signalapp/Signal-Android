package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class LocaleRemoteConfigTest_parseCountryValues {

  private final String               input;
  private final Map<String, Integer> output;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {"1:10000,*:400", new HashMap<String, Integer>() {{
        put("1", 10000);
        put("*", 400);
      }}},
      {"011:1000,1:1000", new HashMap<String, Integer>() {{
        put("011", 1000);
        put("1", 1000);
      }}},
      {"011:1000,1:1000,a:123,abba:abba", new HashMap<String, Integer>() {{
        put("011", 1000);
        put("1", 1000);
        put("a", 123);
        put("abba", 0);
      }}},
      {":,011:1000,1:1000,1:,:1,1:1:1", new HashMap<String, Integer>() {{
        put("011", 1000);
        put("1", 1000);
      }}},
      {"asdf", new HashMap<String, Integer>()},
      {"asdf:", new HashMap<String, Integer>()},
      {":,:,:", new HashMap<String, Integer>()},
      {",,", new HashMap<String, Integer>()},
      {"", new HashMap<String, Integer>()}
    });
  }

  public LocaleRemoteConfigTest_parseCountryValues(String input, Map<String, Integer> output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void parseCountryCounts() {
    assertEquals(output, LocaleRemoteConfig.parseCountryValues(input, 0));
  }

}
