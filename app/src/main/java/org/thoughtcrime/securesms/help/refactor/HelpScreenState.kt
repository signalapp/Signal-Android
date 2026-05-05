/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help.refactor

data class HelpScreenState(
  val problemText: String = "",
  val categoryIndex: Int = 0,
  val selectedFeeling: Feeling? = null,
  val includeDebugLog: Boolean = true,
  val isSubmitting: Boolean = false,
) {
  val isFormValid: Boolean
    get() = problemText.length >= MINIMUM_PROBLEM_CHARS && categoryIndex > 0

  private companion object {
    private const val MINIMUM_PROBLEM_CHARS = 10
  }
}
