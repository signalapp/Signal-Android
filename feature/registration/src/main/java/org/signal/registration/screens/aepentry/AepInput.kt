/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.censor

/**
 * Recovery key text as the user has typed it so far, alongside the normalized form and whatever is currently wrong with
 * it. Every screen that collects a recovery key shares this so they all agree on when a key is malformed or finished.
 *
 * @param enteredText The typed text, preserved verbatim (illegal characters stripped, cut off at a complete key) so #/= stay visible as they are typed.
 * @param normalized Storage-normalized lowercase form of [enteredText], used for validation and submit.
 */
data class AepInput(
  val enteredText: String = "",
  val normalized: String = "",
  val isValid: Boolean = false,
  val error: AepValidationError? = null
) {

  override fun toString(): String = "AepInput(enteredText=${enteredText.censor()}, normalized=${normalized.censor()}, isValid=$isValid, error=$error)"

  companion object {
    /**
     * Normalizes [input], cutting off anything past a complete key, and works out what, if anything, is wrong with what
     * is left. An error the user has already been shown sticks around until it is actually resolved, so [previousError]
     * gets a say in the outcome.
     */
    fun from(input: String, previousError: AepValidationError? = null): AepInput {
      val enteredText = AccountEntropyPool.removeIllegalCharacters(input).take(AccountEntropyPool.LENGTH)
      val normalized = AccountEntropyPool.formatForStorage(enteredText).lowercase()

      val isValid = AccountEntropyPool.isFullyValid(normalized)
      val isComplete = normalized.length == AccountEntropyPool.LENGTH

      val carriedError = when (previousError) {
        AepValidationError.Invalid -> if (isValid) null else previousError
        AepValidationError.Incorrect -> null
        null -> null
      }

      val error = carriedError ?: AepValidationError.Invalid.takeIf { isComplete && !isValid }

      return AepInput(enteredText = enteredText, normalized = normalized, isValid = isValid, error = error)
    }
  }
}
