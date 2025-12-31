package org.signal.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class StringUtilTest_hasMixedTextDirection {

  private final CharSequence input;
  private final boolean      expected;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      { "",        false },
      { null,      false },
      { "A",       false},
      { "A.",      false},
      { "'A'",     false},
      { "A,",      false},
      { "ة",       false},  // Arabic
      { ".ة",      false},  // Arabic
      { "ی",       false},  // Kurdish
      { "ی",       false }, // Farsi
      { "و",       false }, // Urdu
      { "ת",       false }, // Hebrew
      { "ש",       false }, // Yiddish
      { "Aة",       true }, // Arabic-ASCII
      { "A.ة",      true }, // Arabic-ASCII
      { "یA",       true }, // Kurdish-ASCII
      { "Aی",       true }, // Farsi-ASCII
      { "وA",       true }, // Urdu-ASCII
      { "Aת",       true }, // Hebrew-ASCII
      { "שA",       true }, // Yiddish-ASCII
    });
  }

  public StringUtilTest_hasMixedTextDirection(CharSequence input, boolean expected) {
    this.input    = input;
    this.expected = expected;
  }

  @Test
  public void trim() {
    boolean output = BidiUtil.hasMixedTextDirection(input);
    assertEquals(expected, output);
  }
}
