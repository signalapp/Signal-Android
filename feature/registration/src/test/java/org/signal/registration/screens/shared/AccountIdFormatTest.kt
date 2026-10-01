/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.shared

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class AccountIdFormatTest {

  companion object {
    private const val FULL_ID = "a6b284822e3283d07f2391360a4c2b91"
  }

  @Test
  fun `normalize strips the formatting a user may have pasted`() {
    assertThat(AccountIdFormat.normalize("A6B28482-2E32-83D0-7F23-91360A4C2B91")).isEqualTo(FULL_ID)
    assertThat(AccountIdFormat.normalize(" a6b28482 2e32 ")).isEqualTo("a6b284822e32")
  }

  @Test
  fun `normalizeAndTruncate cuts off anything past a complete ID`() {
    assertThat(AccountIdFormat.normalizeAndTruncate(FULL_ID + "ff")).isEqualTo(FULL_ID)
    assertThat(AccountIdFormat.normalizeAndTruncate("A6B28482-2E32-83D0-7F23-91360A4C2B91-FF")).isEqualTo(FULL_ID)
    assertThat(AccountIdFormat.normalizeAndTruncate("a6b28482")).isEqualTo("a6b28482")
  }

  @Test
  fun `text containing a letter reads as an account ID at any length`() {
    assertThat(AccountIdFormat.asAccountIdOrNull("a")).isEqualTo("a")
    assertThat(AccountIdFormat.asAccountIdOrNull("A6B28482-2E32-83D0-7F23-91360A4C2B91")).isEqualTo(FULL_ID)
  }

  @Test
  fun `all-digit text only reads as an account ID once it is longer than any phone number`() {
    assertThat(AccountIdFormat.asAccountIdOrNull("1".repeat(15))).isNull()
    assertThat(AccountIdFormat.asAccountIdOrNull("1".repeat(16))).isEqualTo("1".repeat(16))
  }

  @Test
  fun `text longer than a complete account ID reads as one, cut off at the maximum length`() {
    assertThat(AccountIdFormat.asAccountIdOrNull(FULL_ID + "ff")).isEqualTo(FULL_ID)
  }

  @Test
  fun `a phone number never reads as an account ID`() {
    assertThat(AccountIdFormat.asAccountIdOrNull("+1 555 123 4567")).isNull()
    assertThat(AccountIdFormat.asAccountIdOrNull("(555) 123-4567")).isNull()
    assertThat(AccountIdFormat.asAccountIdOrNull("+447911123456")).isNull()
  }

  @Test
  fun `text outside the hex alphabet never reads as an account ID`() {
    assertThat(AccountIdFormat.asAccountIdOrNull("a6b28482g")).isNull()
    assertThat(AccountIdFormat.asAccountIdOrNull("hello")).isNull()
  }

  @Test
  fun `empty text reads as nothing at all`() {
    assertThat(AccountIdFormat.asAccountIdOrNull("")).isNull()
    assertThat(AccountIdFormat.asAccountIdOrNull("  --  ")).isNull()
  }

  @Test
  fun `validate only complains about the alphabet, never about being mid-entry`() {
    assertThat(AccountIdFormat.validate("a6b28482")).isNull()
    assertThat(AccountIdFormat.validate(FULL_ID)).isNull()
    assertThat(AccountIdFormat.validate("a6b28482g")).isEqualTo(AccountIdError.Invalid)
  }

  @Test
  fun `only a complete, well-formed ID parses as an ACI`() {
    assertThat(AccountIdFormat.toAciOrNull(FULL_ID)?.rawUuid?.toString()).isEqualTo("a6b28482-2e32-83d0-7f23-91360a4c2b91")
    assertThat(AccountIdFormat.toAciOrNull(FULL_ID.dropLast(1))).isNull()
    assertThat(AccountIdFormat.toAciOrNull(FULL_ID + "f")).isNull()
  }
}
