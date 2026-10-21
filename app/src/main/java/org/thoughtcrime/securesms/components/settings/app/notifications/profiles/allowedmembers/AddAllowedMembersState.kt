/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * [AddAllowedMembersScreen] state.
 */
data class AddAllowedMembersState(
  /** The profile being created, or null until it has been read. */
  val profile: NotificationProfile? = null,
  /** The people and groups allowed through while the profile is on. */
  val members: List<Recipient> = emptyList(),
  val snackbar: Snackbar = Snackbar.None
) {

  override fun toString(): String = "AddAllowedMembersState(profileId=${profile?.id}, members=${members.size}, snackbar=${snackbar.javaClass.simpleName})"

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** Confirms that [recipient] was removed, with an option to undo. */
    data class MemberRemoved(val recipient: Recipient) : Snackbar
  }
}
