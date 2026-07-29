/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.addusername

import org.signal.core.util.censor

sealed class AddUsernameScreenEvents {
  /** The user edited the username field. */
  data class UsernameChanged(val value: String) : AddUsernameScreenEvents() {
    override fun toString(): String = "UsernameChanged(value=${value.censor()})"
  }

  /** The user tapped the "learn more" link under the username field. */
  data object LearnMoreClicked : AddUsernameScreenEvents()

  /** The user opted out of choosing a username. */
  data object SkipClicked : AddUsernameScreenEvents()

  /** The user submitted the entered username. */
  data object NextClicked : AddUsernameScreenEvents()

  /** The user dismissed the network error dialog. */
  data object NetworkErrorDialogDismissed : AddUsernameScreenEvents()

  /** The user dismissed the unknown error dialog. */
  data object UnknownErrorDialogDismissed : AddUsernameScreenEvents()

  /** The user dismissed the username-unavailable dialog. */
  data object UsernameUnavailableDialogDismissed : AddUsernameScreenEvents()
}
