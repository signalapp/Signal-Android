package org.thoughtcrime.securesms.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UtilTest {

  @Test
  public void chunk_oneChunk() {
    List<String> input = Arrays.asList("A", "B", "C");

    List<List<String>> output = Util.chunk(input, 3);
    assertEquals(1, output.size());
    assertEquals(input, output.get(0));

    output = Util.chunk(input, 4);
    assertEquals(1, output.size());
    assertEquals(input, output.get(0));

    output = Util.chunk(input, 100);
    assertEquals(1, output.size());
    assertEquals(input, output.get(0));
  }

  @Test
  public void chunk_multipleChunks() {
    List<String> input = Arrays.asList("A", "B", "C", "D", "E");

    List<List<String>> output = Util.chunk(input, 4);
    assertEquals(2, output.size());
    assertEquals(Arrays.asList("A", "B", "C", "D"), output.get(0));
    assertEquals(Arrays.asList("E"), output.get(1));

    output = Util.chunk(input, 2);
    assertEquals(3, output.size());
    assertEquals(Arrays.asList("A", "B"), output.get(0));
    assertEquals(Arrays.asList("C", "D"), output.get(1));
    assertEquals(Arrays.asList("E"), output.get(2));

    output = Util.chunk(input, 1);
    assertEquals(5, output.size());
    assertEquals(Arrays.asList("A"), output.get(0));
    assertEquals(Arrays.asList("B"), output.get(1));
    assertEquals(Arrays.asList("C"), output.get(2));
    assertEquals(Arrays.asList("D"), output.get(3));
    assertEquals(Arrays.asList("E"), output.get(4));
  }
}
