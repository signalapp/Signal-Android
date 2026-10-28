/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.editprofile

import org.signal.core.models.badges.Badge

data class EditProfileState(
  /** The user's profile name, or empty if they haven't set one. */
  val profileName: String = "",
  /** The user's about text, or empty if they haven't set one. */
  val about: String = "",
  /** The emoji beside the about text, or empty for none. */
  val aboutEmoji: String = "",
  /** The user's username, written `nickname.discriminator`, or null if they don't have one. */
  val username: String? = null,
  val usernameSyncState: UsernameSyncState = UsernameSyncState.IN_SYNC,
  /** Whether to point out the username link row, until the user dismisses the tip. */
  val showUsernameLinkTooltip: Boolean = false,
  /** False while the account is unregistered or the app too old to make changes; every row then explains why instead. */
  val canEdit: Boolean = true,
  /** True while an avatar is being saved or the username deleted. */
  val busy: Boolean = false,
  /** The badge a Sustainer gets, shown in [Dialog.BecomeASustainer] once it's loaded. */
  val sustainerBadge: Badge? = null,
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  /** The username link row only shows for a username that isn't broken. */
  val showUsernameLink: Boolean
    get() = username != null && usernameSyncState != UsernameSyncState.USERNAME_AND_LINK_CORRUPTED

  override fun toString(): String {
    return "EditProfileState(profileNameLength=${profileName.length}, aboutLength=${about.length}, hasAboutEmoji=${aboutEmoji.isNotEmpty()}, hasUsername=${username != null}, usernameSyncState=$usernameSyncState, showUsernameLinkTooltip=$showUsernameLinkTooltip, canEdit=$canEdit, busy=$busy, hasSustainerBadge=${sustainerBadge != null}, dialog=$dialog, snackbar=$snackbar)"
  }

  /** Whether the username and its link match what the service has. */
  enum class UsernameSyncState {
    IN_SYNC,
    USERNAME_AND_LINK_CORRUPTED,
    LINK_CORRUPTED
  }

  sealed interface Dialog {
    data object None : Dialog

    /** Offers to edit or delete the user's username. */
    data object UsernameOptions : Dialog

    /** Confirms deleting [username]. */
    data class ConfirmDeleteUsername(val username: String) : Dialog {
      override fun toString(): String = "ConfirmDeleteUsername"
    }

    /** Explains that an unregistered device can't change the profile, and offers to re-register. */
    data object Unregistered : Dialog

    /** Explains that this version of the app is too old to change the profile, and offers to update. */
    data object Deprecated : Dialog

    /** A sheet explaining badges to a user who doesn't have any, and inviting them to become a Sustainer. */
    data object BecomeASustainer : Dialog
  }

  sealed interface Snackbar {
    data object None : Snackbar

    data object UsernameCreated : Snackbar

    data object UsernameDeleted : Snackbar

    data object UsernameDeleteFailed : Snackbar

    /** The chosen photo couldn't be read from the device. */
    data object AvatarReadFailed : Snackbar

    /** The avatar couldn't be uploaded, so nothing changed. */
    data object AvatarNetworkFailure : Snackbar
  }
}
