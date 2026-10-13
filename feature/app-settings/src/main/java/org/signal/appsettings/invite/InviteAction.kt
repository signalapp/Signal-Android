/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.invite

/**
 * One-shot side effects that have to be carried out by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface InviteAction {

  /** Invite user via the share sheet. */
  data class ShareInvite(val inviteText: String) : InviteAction {
    override fun toString(): String = "ShareInvite"
  }

  /** Leave the screen. */
  data object NavigateBack : InviteAction
}
