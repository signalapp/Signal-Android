/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.permissions

import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason

data class PermissionsSettingsState(
  val selfCanEditSettings: Boolean = false,
  val nonAdminCanAddMembers: Boolean = false,
  val nonAdminCanEditGroupInfo: Boolean = false,
  val nonAdminCanSendMessages: Boolean = false,
  val nonAdminCanSetMemberLabel: Boolean = false,
  /** Whether restricting member labels to admins would actually clear anyone's label. */
  val nonAdminsHaveMemberLabels: Boolean = false,
  val dialog: Dialog = Dialog.None,
  /**
   * Why the last change we submitted came back rejected, if it did. The rows render the group's actual permissions
   * rather than what the user asked for, so a rejection needs to say so itself.
   */
  val groupChangeError: GroupChangeFailureReason? = null
) {

  sealed interface Dialog {
    data object None : Dialog
    data object MemberLabelsWillBeCleared : Dialog
  }
}
