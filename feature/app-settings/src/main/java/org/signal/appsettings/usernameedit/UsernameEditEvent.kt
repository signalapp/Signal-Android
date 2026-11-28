/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernameedit

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface UsernameEditEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : UsernameEditEvent

  /** The user edited the part of the username before the dot. */
  data class NicknameChanged(val nickname: String) : UsernameEditEvent {
    override fun toString(): String = "NicknameChanged(length=${nickname.length})"
  }

  /** The user edited the digits after the dot. */
  data class DiscriminatorChanged(val discriminator: String) : UsernameEditEvent {
    override fun toString(): String = "DiscriminatorChanged(length=${discriminator.length})"
  }

  /** The user tapped save, or the keyboard's done action on the discriminator. */
  data object SubmitClicked : UsernameEditEvent

  /** The user tapped delete, which only shows once they've cleared an existing username. */
  data object DeleteClicked : UsernameEditEvent

  /** The user agreed, in [UsernameEditState.Dialog.ConfirmReset], to get a new username link. */
  data object ResetConfirmed : UsernameEditEvent

  /** The user agreed, in [UsernameEditState.Dialog.ConfirmSameUsernameRecovery], to save the username they already had. */
  data object SameUsernameRecoveryConfirmed : UsernameEditEvent

  /** Dismisses whatever is in [UsernameEditState.dialog]. */
  data object DialogDismissed : UsernameEditEvent

  /** The user tapped the learn more link below the field. */
  data class LearnMoreClicked(val url: String) : UsernameEditEvent

  /** Whatever was in [UsernameEditState.snackbar] has gone. */
  data object SnackbarDismissed : UsernameEditEvent
}
