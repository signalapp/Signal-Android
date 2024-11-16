/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.core.util.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class LogTest {
  @Test
  fun tag_short_class_name() {
    assertEquals("MyClass", Log.tag(MyClass::class))
  }

  @Test
  fun tag_23_character_class_name() {
    val tag = Log.tag(TwentyThreeCharacters23::class)
    assertEquals("TwentyThreeCharacters23", tag)
    assertEquals(23, tag.length)
  }

  @Test
  fun tag_24_character_class_name() {
    assertEquals(24, TwentyFour24Characters24::class.simpleName!!.length)
    val tag = Log.tag(TwentyFour24Characters24::class)
    assertEquals("TwentyFour24Characters2", tag)
    assertEquals(23, Log.tag(TwentyThreeCharacters23::class).length)
  }

  private inner class MyClass

  private inner class TwentyThreeCharacters23

  private inner class TwentyFour24Characters24
}
