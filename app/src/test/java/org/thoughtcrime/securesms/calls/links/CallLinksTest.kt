/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.EmptyLogger

/**
 * See [CallLinks]
 */
class CallLinksTest {
  companion object {
    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  @Test
  fun `parseUrl returns null for malformed percent escape instead of throwing`() {
    assertNull(CallLinks.parseUrl("https://signal.link/call/#key=abcdef&n=%ZZ"))
  }

  @Test
  fun `parseUrl returns null for malformed percent escape in key instead of throwing`() {
    assertNull(CallLinks.parseUrl("https://signal.link/call/#key=%ZZ"))
  }
}
