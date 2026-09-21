/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.chatsettings.screens.sharablegrouplink

import androidx.annotation.StringRes

data class ShareableGroupLinkState(
  val groupLink: GroupLink = GroupLink.NONE,
  /** Whether a change is in flight. Every change here is a group change, so it takes a network round trip. */
  val busy: Boolean = false,
  val dialog: Dialog = Dialog.None,
  /**
   * Why the last change we submitted came back rejected, if it did. The rows render the group's actual link rather
   * than what the user asked for, so a rejection needs to say so itself.
   */
  @get:StringRes val errorMessage: Int? = null
) {

  sealed interface Dialog {
    data object None : Dialog
    data object ConfirmResetLink : Dialog
  }
}
