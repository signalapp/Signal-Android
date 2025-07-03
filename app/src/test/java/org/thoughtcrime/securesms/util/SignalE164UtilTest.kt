/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockkObject
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.keyvalue.SignalStore

class SignalE164UtilTest {

  @Before
  fun setup() {
    mockkObject(SignalStore)
    every { SignalStore.account.e164 } returns "+11234567890"
  }

  @Test
  fun `isPotentialNonShortCodeE164 - valid`() {
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("+1234567890")).isTrue()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("1234567")).isTrue()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("1234568")).isTrue()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("12345679")).isTrue()
  }

  @Test
  fun `isPotentialNonShortCodeE164 - invalid, no leading characters`() {
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("1")).isFalse()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("12")).isFalse()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("123")).isFalse()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("12345")).isFalse()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("123456")).isFalse()
  }

  @Test
  fun `isPotentialNonShortCodeE164 - invalid, leading plus sign`() {
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("+123456")).isFalse()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("++123456")).isFalse()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("+++123456")).isFalse()
  }

  @Test
  fun `isPotentialNonShortCodeE164 - invalid, leading zeros`() {
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("0123456")).isFalse()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("00123456")).isFalse()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("000123456")).isFalse()
  }

  @Test
  fun `isPotentialNonShortCodeE164 - invalid, mix of leading characters`() {
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("+0123456")).isFalse()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("0+0123456")).isFalse()
    assertThat(SignalE164Util.isPotentialNonShortCodeE164("+0+123456")).isFalse()
  }
}
