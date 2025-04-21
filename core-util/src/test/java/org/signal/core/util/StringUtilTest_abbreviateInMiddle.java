package org.signal.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

@RunWith(Parameterized.class)
public final class StringUtilTest_abbreviateInMiddle {

  @Parameterized.Parameter(0)
  public CharSequence input;

  @Parameterized.Parameter(1)
  public int maxChars;

  @Parameterized.Parameter(2)
  public CharSequence expected;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
            {null, 0, null},
            {null, 1, null},
            {"", 0, ""},
            {"", 1, ""},
            {"0123456789", 10, "0123456789"},
            {"0123456789", 11, "0123456789"},
            {"0123456789", 9, "0123…6789"},
            {"0123456789", 8, "012…6789"},
            {"0123456789", 7, "012…789"},
            {"0123456789", 6, "01…789"},
            {"0123456789", 5, "01…89"},
            {"0123456789", 4, "0…89"},
            {"0123456789", 3, "0…9"},
            });
  }

  @Test
  public void abbreviateInMiddle() {
    CharSequence output = StringUtil.abbreviateInMiddle(input, maxChars);
    assertEquals(expected, output);
    if (Objects.equals(input, output)) {
      assertSame(output, input);
    } else {
      assertNotNull(output);
      assertEquals(maxChars, output.length());
    }
  }
}
