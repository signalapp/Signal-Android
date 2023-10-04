/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details

import org.junit.Assert.assertEquals
import org.junit.Test

class IBANValidatorTest {
  companion object {
    private const val VALID_IBAN = "GB82WEST12345698765432"
    private const val INVALID_IBAN = "GB82WEST12335698765432"
    private const val INVALID_COUNTRY = "US82WEST12335698765432"
  }

  @Test
  fun `Given a blank IBAN, when I validate, then I expect POTENTIALLY_VALID`() {
    val actual = IBANValidator.validate("", false)

    assertEquals(IBANValidator.Validity.POTENTIALLY_VALID, actual)
  }

  @Test
  fun `Given a valid IBAN, when I validate, then I expect COMPLETELY_VALID`() {
    val actual = IBANValidator.validate(VALID_IBAN, false)

    assertEquals(IBANValidator.Validity.COMPLETELY_VALID, actual)
  }

  @Test
  fun `Given an invalid IBAN, when I validate, then I expect INVALID_MOD_97`() {
    val actual = IBANValidator.validate(INVALID_IBAN, false)

    assertEquals(IBANValidator.Validity.INVALID_MOD_97, actual)
  }

  @Test
  fun `Given an invalid country, when I validate, then I expect INVALID_COUNTRY`() {
    val actual = IBANValidator.validate(INVALID_COUNTRY, false)

    assertEquals(IBANValidator.Validity.INVALID_COUNTRY, actual)
  }

  @Test
  fun `Given too short and not focused, when I validate, then I expect TOO_SHORT`() {
    val actual = IBANValidator.validate(VALID_IBAN.dropLast(5), false)

    assertEquals(IBANValidator.Validity.TOO_SHORT, actual)
  }

  @Test
  fun `Given too short and focused, when I validate, then I expect POTENTIALLY_VALID`() {
    val actual = IBANValidator.validate(VALID_IBAN.dropLast(5), true)

    assertEquals(IBANValidator.Validity.POTENTIALLY_VALID, actual)
  }

  @Test
  fun `Given too long, when I validate, then I expect TOO_LONG`() {
    val actual = IBANValidator.validate(VALID_IBAN + "A", false)

    assertEquals(IBANValidator.Validity.TOO_LONG, actual)
  }
}
