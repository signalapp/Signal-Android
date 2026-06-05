package org.signal.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class StringUtilTest_whitespace_handling {

  private final String  input;
  private final String  expectedTrimmed;
  private final boolean isVisuallyEmpty;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{

      { "", "", true },
      { " ", "", true },
      { "A", "A", false },
      { " B", "B", false },
      { "C ", "C", false },

      /* Unicode whitespace */
      { "\u200E", "", true },
      { "\u200F", "", true },
      { "\u2007", "", true },
      { "\u200B", "", true },
      { "\u2800", "", true },
      { "\u2007\u200FA\tB\u200EC\u200E\u200F", "A\tB\u200EC", false },

      /* No-break spaces */
      { "\u00A0", "", true },
      { "\u202F", "", true },

      /* Format characters */
      { "\u200C", "", true },
      { "\u200D", "", true },
      { "\u2060", "", true },
      { "\uFEFF", "", true },

      /* Additional invisible characters */
      { "\u034F", "", true },
      { "\u2800", "", true },
      { "\u3164", "", true },
      { "\uFFA0", "", true },
      { "\u115F", "", true },
      { "\u1160", "", true },

      /* Mixed invisible characters should still be empty */
      { "\u00A0\u200D\u2060\uFEFF", "", true },

    });
  }

  public StringUtilTest_whitespace_handling(String input, String expectedTrimmed, boolean isVisuallyEmpty) {
    this.input           = input;
    this.expectedTrimmed = expectedTrimmed;
    this.isVisuallyEmpty = isVisuallyEmpty;
  }

  @Test
  public void isVisuallyEmpty() {
    assertEquals(isVisuallyEmpty, StringUtil.isVisuallyEmpty(input));
  }

  @Test
  public void trim() {
    assertEquals(expectedTrimmed, StringUtil.trimToVisualBounds(input));
  }

}