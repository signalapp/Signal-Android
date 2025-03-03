package org.signal.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

@RunWith(Parameterized.class)
public final class StringUtilTest_trim {

  private final CharSequence input;
  private final CharSequence expected;
  private final boolean      changed;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      { "",        "",       false },
      { " ",       "",       true },
      { "   ",     "",       true },
      { "\n",      "",       true},
      { "\n\n\n",  "",       true },
      { "A",       "A",      false },
      { "A ",      "A",      true },
      { " A",      "A",      true },
      { " A ",     "A",      true },
      { "\nA\n",   "A",      true },
      { "A\n\n",   "A",      true },
      { "A\n\nB",  "A\n\nB", false },
      { "A\n\nB ", "A\n\nB", true },
      { "A  B",    "A  B",   false },
    });
  }

  public StringUtilTest_trim(CharSequence input, CharSequence expected, boolean changed) {
    this.input    = input;
    this.expected = expected;
    this.changed  = changed;
  }

  @Test
  public void trim() {
    CharSequence output = StringUtil.trim(input);
    assertEquals(expected, output);

    if (changed) {
      assertNotSame(output, input);
    } else {
      assertSame(output, input);
    }
  }

}