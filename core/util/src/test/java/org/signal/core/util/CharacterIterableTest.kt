package org.signal.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterIterableTest {
  @Test
  fun `empty string yields no graphemes`() {
    assertEquals(emptyList<String>(), CharacterIterable("").toList())
  }

  @Test
  fun `single character yields one grapheme`() {
    assertEquals(listOf("a"), CharacterIterable("a").toList())
  }

  @Test
  fun `multiple characters yield one grapheme each`() {
    assertEquals(listOf("a", "b", "c"), CharacterIterable("abc").toList())
  }

  @Test
  fun `surrogate pair is kept as one grapheme`() {
    assertEquals(listOf("a", "👤", "b"), CharacterIterable("a👤b").toList())
  }
}
