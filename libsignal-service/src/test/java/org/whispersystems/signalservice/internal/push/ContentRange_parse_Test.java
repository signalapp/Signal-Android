package org.whispersystems.signalservice.internal.push;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class ContentRange_parse_Test {

  @Parameterized.Parameter(0)
  public String input;

  @Parameterized.Parameter(1)
  public int expectedRangeStart;

  @Parameterized.Parameter(2)
  public int expectedRangeEnd;

  @Parameterized.Parameter(3)
  public int expectedSize;

  @Parameterized.Parameters(name = "Content-Range: \"{0}\"")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      { "versions 1-2/3",     1,  2,  3 },
      { "versions 23-45/67", 23, 45, 67 },
    });
  }

  @Test
  public void rangeStart() {
    assertEquals(expectedRangeStart, ContentRange.parse(input).get().getRangeStart());
  }

  @Test
  public void rangeEnd() {
    assertEquals(expectedRangeEnd, ContentRange.parse(input).get().getRangeEnd());
  }

  @Test
  public void totalSize() {
    assertEquals(expectedSize, ContentRange.parse(input).get().getTotalSize());
  }
}
