/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import java.util.LinkedList
import java.util.Random

class MockRandom(initialInts: List<Int>) : Random() {

  val nextInts = LinkedList(initialInts)

  override fun nextInt(): Int {
    return nextInts.remove()
  }
}
