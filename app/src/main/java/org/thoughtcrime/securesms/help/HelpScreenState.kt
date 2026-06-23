/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

data class HelpScreenState(
  val problemText: String = "",
  val categoryIndex: Int = 0,
  val selectedFeeling: Feeling? = null,
  val includeDebugLog: Boolean = true,
  val isSubmitting: Boolean = false,
  val displayValidationErrors: Boolean = false
) {
  val isTextValid: Boolean = problemText.length >= MINIMUM_PROBLEM_CHARS
  val isCategoryValid: Boolean = categoryIndex > 0

  val isFormValid: Boolean = isTextValid && isCategoryValid

  companion object {
    const val MINIMUM_PROBLEM_CHARS = 10
  }
}
