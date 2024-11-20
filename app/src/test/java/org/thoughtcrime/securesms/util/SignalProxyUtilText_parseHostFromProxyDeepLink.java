package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class SignalProxyUtilText_parseHostFromProxyDeepLink {

  private final String input;
  private final String output;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        { "https://signal.tube/#proxy.parker.org",     "proxy.parker.org" },
        { "https://signal.tube/#proxy.parker.org:443", "proxy.parker.org" },
        { "sgnl://signal.tube/#proxy.parker.org",      "proxy.parker.org" },
        { "sgnl://signal.tube/#proxy.parker.org:443",  "proxy.parker.org" },
        { "https://signal.tube/",                       null },
        { "https://signal.tube/#",                      null },
        { "sgnl://signal.tube/",                        null },
        { "sgnl://signal.tube/#",                       null },
        { "http://signal.tube/#proxy.parker.org",       null },
        { "signal.tube/#proxy.parker.org",              null },
        { "",                                           null },
        { null,                                         null }
    });
  }

  public SignalProxyUtilText_parseHostFromProxyDeepLink(String input, String output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void parse() {
    assertEquals(output, SignalProxyUtil.parseHostFromProxyDeepLink(input));
  }
}
