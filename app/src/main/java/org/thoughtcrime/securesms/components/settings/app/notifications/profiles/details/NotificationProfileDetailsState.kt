/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * [NotificationProfileDetailsScreen] state.
 */
data class NotificationProfileDetailsState(
  /** The profile being shown, or null until it has been read. */
  val profile: NotificationProfile? = null,
  /** The people and groups allowed through while the profile is on. */
  val members: List<Recipient> = emptyList(),
  /** How long the profile stays on, or null if it is off. */
  val activeStatus: NotificationProfiles.ActiveStatus? = null,
  /** Whether the user asked to see every member rather than the first few. */
  val expanded: Boolean = false,
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  companion object {
    const val COLLAPSED_MEMBER_COUNT = 5
  }

  val visibleMembers: List<Recipient>
    get() = if (expanded) members else members.take(COLLAPSED_MEMBER_COUNT)

  val showSeeAll: Boolean
    get() = visibleMembers.size < members.size

  override fun toString(): String {
    return "NotificationProfileDetailsState(profileId=${profile?.id}, members=${members.size}, activeStatus=$activeStatus, expanded=$expanded, dialog=$dialog, snackbar=${snackbar.javaClass.simpleName})"
  }

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Asks the user to confirm deleting the profile. */
    data object ConfirmDelete : Dialog
  }

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** Confirms that [recipient] was removed, with an option to undo. */
    data class MemberRemoved(val recipient: Recipient) : Snackbar
  }
}
