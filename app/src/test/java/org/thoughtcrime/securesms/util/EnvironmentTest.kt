package org.thoughtcrime.securesms.util

import org.junit.Assert.assertFalse
import org.junit.Test

class EnvironmentTest {
  @Test
  fun `USE_NEW_REGISTRATION must be false for release`() {
    assertFalse("USE_NEW_REGISTRATION must not be committed as true!", Environment.USE_NEW_REGISTRATION)
  }
}
