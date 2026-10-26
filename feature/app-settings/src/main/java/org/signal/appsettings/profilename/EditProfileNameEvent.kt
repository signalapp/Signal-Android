/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.profilename

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface EditProfileNameEvent {

  /** The user tapped the navigation (close) icon. */
  data object NavigateBackClicked : EditProfileNameEvent

  /** The user edited the first name field. */
  data class GivenNameChanged(val name: String) : EditProfileNameEvent {
    override fun toString(): String = "GivenNameChanged(length=${name.length})"
  }

  /** The user edited the last name field. */
  data class FamilyNameChanged(val name: String) : EditProfileNameEvent {
    override fun toString(): String = "FamilyNameChanged(length=${name.length})"
  }

  /** The user tapped save, or the keyboard's done action on the last field. */
  data object SaveClicked : EditProfileNameEvent

  /** Whatever was in [EditProfileNameState.snackbar] has gone. */
  data object SnackbarDismissed : EditProfileNameEvent
}
