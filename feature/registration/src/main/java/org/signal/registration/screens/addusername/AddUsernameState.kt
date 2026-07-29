/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.addusername

import org.signal.core.util.censor

/**
 * State for the optional username entry screen.
 */
data class AddUsernameState(
  val username: String = "",
  /** Set when the entered nickname fails validation, describing why. */
  val validationError: ValidationError? = null,
  val showSpinner: Boolean = false,
  val dialogs: Dialogs = Dialogs()
) {
  /** Whether the entered nickname is complete enough to submit. */
  val isSubmittable: Boolean
    get() = !showSpinner && username.isNotBlank() && validationError == null

  override fun toString(): String = "AddUsernameState(username=${username.censor()}, validationError=$validationError, showSpinner=$showSpinner, dialogs=$dialogs)"

  enum class ValidationError {
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARACTERS,
    CANNOT_START_WITH_DIGIT
  }

  data class Dialogs(
    val networkError: Boolean = false,
    val unknownError: Boolean = false,
    /** The nickname is valid but no discriminator was available for it. */
    val usernameUnavailable: Boolean = false
  )
}
