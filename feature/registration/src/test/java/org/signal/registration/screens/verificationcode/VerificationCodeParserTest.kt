/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class VerificationCodeParserTest {

  @Test
  fun `parses hyphenated code`() {
    assertThat(VerificationCodeParser.parse("<#> Your Signal code: 123-456 abcd1234efg")).isEqualTo("123456")
  }

  @Test
  fun `parses non-hyphenated code`() {
    assertThat(VerificationCodeParser.parse("Your Signal code: 123456")).isEqualTo("123456")
  }

  @Test
  fun `returns null for message without a code`() {
    assertThat(VerificationCodeParser.parse("Your Signal code is on its way")).isNull()
  }

  @Test
  fun `returns null for null message`() {
    assertThat(VerificationCodeParser.parse(null)).isNull()
  }
}
