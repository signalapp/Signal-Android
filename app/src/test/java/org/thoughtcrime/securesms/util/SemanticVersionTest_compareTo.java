package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class SemanticVersionTest_compareTo {

  private final SemanticVersion first;
  private final SemanticVersion second;
  private final int             output;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        { new SemanticVersion(1, 0, 0), new SemanticVersion(0, 1, 0),  1 },
        { new SemanticVersion(1, 0, 0), new SemanticVersion(0, 0, 1),  1 },
        { new SemanticVersion(1, 0, 0), new SemanticVersion(0, 0, 0),  1 },
        { new SemanticVersion(0, 1, 0), new SemanticVersion(0, 0, 1),  1 },
        { new SemanticVersion(0, 1, 0), new SemanticVersion(0, 0, 0),  1 },
        { new SemanticVersion(0, 0, 1), new SemanticVersion(0, 0, 0),  1 },
        { new SemanticVersion(1, 1, 0), new SemanticVersion(1, 0, 0),  1 },
        { new SemanticVersion(1, 1, 1), new SemanticVersion(1, 1, 0),  1 },
        { new SemanticVersion(0, 0, 1), new SemanticVersion(1, 0, 0), -1 },
        { new SemanticVersion(1, 1, 1), new SemanticVersion(1, 1, 1),  0 },
        { new SemanticVersion(0, 0, 0), new SemanticVersion(0, 0, 0),  0 },
    });
  }

  public SemanticVersionTest_compareTo(SemanticVersion first, SemanticVersion second, int output) {
    this.first  = first;
    this.second = second;
    this.output = output;
  }

  @Test
  public void compareTo() {
    assertEquals(output, first.compareTo(second));
  }
}
