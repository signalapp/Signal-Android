package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class SignalProxyUtilText_generateProxyUrl {

  private final String input;
  private final String output;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        { "https://signal.tube/#proxy.parker.org",     "https://signal.tube/#proxy.parker.org" },
        { "https://signal.tube/#proxy.parker.org:443", "https://signal.tube/#proxy.parker.org" },
        { "sgnl://signal.tube/#proxy.parker.org",      "https://signal.tube/#proxy.parker.org" },
        { "sgnl://signal.tube/#proxy.parker.org:443",  "https://signal.tube/#proxy.parker.org" },
        { "proxy.parker.org",                          "https://signal.tube/#proxy.parker.org" },
        { "proxy.parker.org:443",                      "https://signal.tube/#proxy.parker.org" },
        { "x",                                         "https://signal.tube/#x" },
        { "",                                          "https://signal.tube/#" }
    });
  }

  public SignalProxyUtilText_generateProxyUrl(String input, String output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void parse() {
    assertEquals(output, SignalProxyUtil.generateProxyUrl(input));
  }
}
