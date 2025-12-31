package org.signal.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ListUtilTest {
  @Test
  fun chunk_oneChunk() {
    val input = listOf("A", "B", "C")

    var output = ListUtil.chunk(input, 3)
    assertEquals(1, output.size)
    assertEquals(input, output[0])

    output = ListUtil.chunk(input, 4)
    assertEquals(1, output.size)
    assertEquals(input, output[0])

    output = ListUtil.chunk(input, 100)
    assertEquals(1, output.size)
    assertEquals(input, output[0])
  }

  @Test
  fun chunk_multipleChunks() {
    val input: List<String> = listOf("A", "B", "C", "D", "E")

    var output = ListUtil.chunk(input, 4)
    assertEquals(2, output.size)
    assertEquals(listOf("A", "B", "C", "D"), output[0])
    assertEquals(listOf("E"), output[1])

    output = ListUtil.chunk(input, 2)
    assertEquals(3, output.size)
    assertEquals(listOf("A", "B"), output[0])
    assertEquals(listOf("C", "D"), output[1])
    assertEquals(listOf("E"), output[2])

    output = ListUtil.chunk(input, 1)
    assertEquals(5, output.size)
    assertEquals(listOf("A"), output[0])
    assertEquals(listOf("B"), output[1])
    assertEquals(listOf("C"), output[2])
    assertEquals(listOf("D"), output[3])
    assertEquals(listOf("E"), output[4])
  }
}
