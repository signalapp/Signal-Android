/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.chatsettings.screens.grouppermissions

/**
 * Everything the permissions screen reads off of the group's record.
 */
data class GroupPermissions(
  val selfCanEditSettings: Boolean,
  val nonAdminCanAddMembers: Boolean,
  val nonAdminCanEditGroupInfo: Boolean,
  val nonAdminCanSendMessages: Boolean,
  val nonAdminCanSetMemberLabel: Boolean,
  /** Whether restricting member labels to admins would actually clear anyone's label. */
  val nonAdminsHaveMemberLabels: Boolean
) {

  companion object {
    /** What the screen shows until the group's permissions arrive: everything locked down. */
    val NONE = GroupPermissions(
      selfCanEditSettings = false,
      nonAdminCanAddMembers = false,
      nonAdminCanEditGroupInfo = false,
      nonAdminCanSendMessages = false,
      nonAdminCanSetMemberLabel = false,
      nonAdminsHaveMemberLabels = false
    )
  }
}
