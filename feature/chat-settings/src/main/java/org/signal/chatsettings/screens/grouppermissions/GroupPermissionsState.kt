/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.chatsettings.screens.grouppermissions

import androidx.annotation.StringRes

data class GroupPermissionsState(
  val permissions: GroupPermissions = GroupPermissions.NONE,
  val dialog: Dialog = Dialog.None,
  /**
   * Why the last change we submitted came back rejected, if it did. The rows render the group's actual permissions
   * rather than what the user asked for, so a rejection needs to say so itself.
   */
  @get:StringRes val errorMessage: Int? = null
) {

  sealed interface Dialog {
    data object None : Dialog
    data object MemberLabelsWillBeCleared : Dialog
  }
}
