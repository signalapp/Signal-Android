/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.addusername

import org.signal.core.util.censor
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.usernames.Username
import org.signal.network.service.UsernameService.ReserveUsernameError

sealed class AddUsernameScreenEvents {
  /** The user edited the username field. */
  data class UsernameChanged(val value: String) : AddUsernameScreenEvents() {
    override fun toString(): String = "UsernameChanged(value=${value.censor()})"
  }

  /** The user edited the discriminator field. */
  data class DiscriminatorChanged(val value: String) : AddUsernameScreenEvents() {
    override fun toString(): String = "DiscriminatorChanged(value=${value.censor()})"
  }

  /**
   * Internal: the user paused typing long enough for the entered username to be validated and reserved. A null
   * [discriminator] means the service should assign one.
   */
  data class EntrySettled(val nickname: String, val discriminator: String?) : AddUsernameScreenEvents() {
    override fun toString(): String = "EntrySettled(nickname=${nickname.censor()}, discriminator=${discriminator?.censor()})"
  }

  /** Internal: a reservation attempt for [nickname] and [discriminator] finished with [result]. */
  data class ReservationCompleted(val nickname: String, val discriminator: String?, val result: RequestResult<Username, ReserveUsernameError>) : AddUsernameScreenEvents() {
    override fun toString(): String = "ReservationCompleted(nickname=${nickname.censor()}, discriminator=${discriminator?.censor()}, result=${result.javaClass.simpleName})"
  }

  /** The user tapped the "learn more" link under the username field. */
  data object LearnMoreClicked : AddUsernameScreenEvents()

  /** The user dismissed the dialog explaining the digits after the username. */
  data object LearnMoreDialogDismissed : AddUsernameScreenEvents()

  /** The user tapped the skip button, asking to opt out of choosing a username. */
  data object SkipClicked : AddUsernameScreenEvents()

  /** The user confirmed they want to skip choosing a username. */
  data object SkipConfirmed : AddUsernameScreenEvents()

  /** The user dismissed the dialog confirming they want to skip choosing a username. */
  data object SkipDialogDismissed : AddUsernameScreenEvents()

  /** The user submitted the entered username. */
  data object NextClicked : AddUsernameScreenEvents()

  /** The user dismissed the network error dialog. */
  data object NetworkErrorDialogDismissed : AddUsernameScreenEvents()

  /** The user dismissed the unknown error dialog. */
  data object UnknownErrorDialogDismissed : AddUsernameScreenEvents()

  /** The user dismissed the username-unavailable dialog. */
  data object UsernameUnavailableDialogDismissed : AddUsernameScreenEvents()

  /** The user dismissed the rate-limited dialog. */
  data object RateLimitedDialogDismissed : AddUsernameScreenEvents()

  /** The user dismissed the reservation-lapsed dialog. */
  data object ReservationLapsedDialogDismissed : AddUsernameScreenEvents()
}
