package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class SemanticVersionTest_parse {

  private final String          input;
  private final SemanticVersion output;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        { "0.0.0",            new SemanticVersion(0, 0, 0)},
        { "1.2.3",            new SemanticVersion(1, 2, 3)},
        { "111.222.333",      new SemanticVersion(111, 222, 333)},
        { "v1.2.3",           null },
        { "1.2.3x",           null },
        { "peter.ben.parker", null },
        { "",                 null}
    });
  }

  public SemanticVersionTest_parse(String input, SemanticVersion output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void parse() {
    assertEquals(output, SemanticVersion.parse(input));
  }
}
