package org.signal.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.signal.core.util.StringUtil;

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
