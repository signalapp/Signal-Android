/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.thoughtcrime.securesms.registration.ui.restore.AccountEntropyPoolVerification.AEPValidationError

@RunWith(Parameterized::class)
class AccountEntropyPoolVerificationTest(
  private val inputBackupKey: String,
  private val inputChanged: Boolean,
  private val inputPreviousError: AEPValidationError?,
  private val expectedIsValid: Boolean,
  private val expectedError: AEPValidationError?
) {

  @Test
  fun verifyAEPValid() {
    val (valid, error) = AccountEntropyPoolVerification.verifyAEP(inputBackupKey, inputChanged, inputPreviousError)

    assertThat(valid).apply { if (expectedIsValid) isTrue() else isFalse() }
    assertThat(error).isEqualTo(expectedError)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "data[{index}]: verify(\"{0}\", {1}, {2}) = ({3}, {4})")
    fun data(): Iterable<Array<Any?>> = listOf(
      TestData(inputBackupKey = "", inputChanged = false, inputPreviousError = null, expectedIsValid = false, expectedError = null),
      TestData(inputBackupKey = "abcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcd", inputChanged = false, inputPreviousError = null, expectedIsValid = true, expectedError = null),
      TestData(inputBackupKey = "abcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcda", inputChanged = true, inputPreviousError = null, expectedIsValid = false, expectedError = AEPValidationError.TooLong(65, 64)),
      TestData(inputBackupKey = "abcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcd", inputChanged = true, inputPreviousError = AEPValidationError.TooLong(65, 64), expectedIsValid = true, expectedError = null),
      TestData(inputBackupKey = "abcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcd", inputChanged = false, inputPreviousError = AEPValidationError.Incorrect, expectedIsValid = true, expectedError = AEPValidationError.Incorrect),
      TestData(inputBackupKey = "abcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcd", inputChanged = true, inputPreviousError = AEPValidationError.Incorrect, expectedIsValid = true, expectedError = null),
      TestData(inputBackupKey = "!@#$!@#!@#!@#%asdf#$@#$@#asdf++dabcdabcdabcdabcdabcdabcdabcdabcd", inputChanged = true, inputPreviousError = null, expectedIsValid = false, expectedError = AEPValidationError.Invalid),
      TestData(inputBackupKey = "abcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcd", inputChanged = true, inputPreviousError = AEPValidationError.Invalid, expectedIsValid = true, expectedError = null),
      TestData(inputBackupKey = "!@#$!@#!@#!@#%asdf#$@#$@#asdf++dabcdabcdabcdabcdabcdabcdabcdabcd", inputChanged = true, inputPreviousError = AEPValidationError.Invalid, expectedIsValid = false, expectedError = AEPValidationError.Invalid),
      TestData(inputBackupKey = "abcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdab", inputChanged = true, inputPreviousError = AEPValidationError.TooLong(65, 64), expectedIsValid = false, expectedError = AEPValidationError.TooLong(66, 64))

    ).map { it.toArray() }
  }

  class TestData(
    private val inputBackupKey: String,
    private val inputChanged: Boolean,
    private val inputPreviousError: AEPValidationError?,
    private val expectedIsValid: Boolean,
    private val expectedError: AEPValidationError?
  ) {
    fun toArray(): Array<Any?> {
      return arrayOf(inputBackupKey, inputChanged, inputPreviousError, expectedIsValid, expectedError)
    }
  }
}
