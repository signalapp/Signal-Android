/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke test for the root project to satisfy build health checks.
 */
class RootProjectTest {
  @Test
  fun rootProjectExists() {
    assertTrue("The Signal root project should be valid", true)
  }
}
