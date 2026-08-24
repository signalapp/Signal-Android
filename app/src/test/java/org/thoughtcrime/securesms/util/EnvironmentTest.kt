/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import assertk.assertThat
import assertk.assertions.isFalse
import org.junit.Test

/**
 * Guards flags in [Environment] that are only meant to be flipped on for local development.
 */
class EnvironmentTest {

  /**
   * The phone-numberless registration flow is incomplete. If this test fails, someone left the flag enabled after
   * testing locally. Do not "fix" it by updating the test.
   */
  @Test
  fun `phone-numberless registration is disabled`() {
    assertThat(Environment.PHONENUMBERLESS_REGISTRATION).isFalse()
  }

  @Test
  fun `MOCK_PHONE_NUMBERLESS_REGISTRATION is disabled`() {
    assertThat(Environment.MOCK_PHONE_NUMBERLESS_REGISTRATION).isFalse()
  }
}
