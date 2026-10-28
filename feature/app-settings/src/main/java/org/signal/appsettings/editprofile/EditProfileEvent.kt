/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.editprofile

import org.signal.core.models.media.Media

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface EditProfileEvent {

  /** The screen came back to the foreground, so the username and account state may have changed. */
  data object ScreenResumed : EditProfileEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : EditProfileEvent

  /** The user tapped their avatar. */
  data object AvatarClicked : EditProfileEvent

  /** The user tapped the edit photo button. */
  data object EditPhotoClicked : EditProfileEvent

  /** The user picked a new avatar in the avatar picker. */
  data class AvatarSelected(val media: Media) : EditProfileEvent {
    override fun toString(): String = "AvatarSelected"
  }

  /** The user removed their avatar in the avatar picker. */
  data object AvatarCleared : EditProfileEvent

  /** The user tapped the profile name row. */
  data object NameClicked : EditProfileEvent

  /** The user tapped the about row. */
  data object AboutClicked : EditProfileEvent

  /** The user tapped the badges row. */
  data object BadgesClicked : EditProfileEvent

  /** The user tapped the button in [EditProfileState.Dialog.BecomeASustainer]. */
  data object BecomeASustainerClicked : EditProfileEvent

  /** The user tapped the username row. */
  data object UsernameClicked : EditProfileEvent

  /** The user chose to edit their username, in [EditProfileState.Dialog.UsernameOptions]. */
  data object EditUsernameClicked : EditProfileEvent

  /** The user chose to delete their username, in [EditProfileState.Dialog.UsernameOptions]. */
  data object DeleteUsernameClicked : EditProfileEvent

  /** The user confirmed, in [EditProfileState.Dialog.ConfirmDeleteUsername], that they want their username deleted. */
  data object DeleteUsernameConfirmed : EditProfileEvent

  /** The user tapped the username link row. */
  data object UsernameLinkClicked : EditProfileEvent

  /** The user closed the tip pointing out the username link. */
  data object UsernameLinkTooltipDismissed : EditProfileEvent

  /** The user chose to update the app, in [EditProfileState.Dialog.Deprecated]. */
  data object UpdateAppClicked : EditProfileEvent

  /** The user chose to re-register, in [EditProfileState.Dialog.Unregistered]. */
  data object ReregisterClicked : EditProfileEvent

  /** Dismisses whatever is in [EditProfileState.dialog]. */
  data object DialogDismissed : EditProfileEvent

  /** Whatever was in [EditProfileState.snackbar] has gone. */
  data object SnackbarDismissed : EditProfileEvent
}
