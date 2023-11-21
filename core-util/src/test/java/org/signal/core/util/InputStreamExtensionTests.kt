/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class InputStreamExtensionTests {

  @Test
  fun `when I call readLength, it returns the correct length`() {
    for (i in 1..10) {
      val bytes = ByteArray(Random.nextInt(from = 512, until = 8092))
      val length = bytes.inputStream().readLength()
      assertEquals(bytes.size.toLong(), length)
    }
  }
}
