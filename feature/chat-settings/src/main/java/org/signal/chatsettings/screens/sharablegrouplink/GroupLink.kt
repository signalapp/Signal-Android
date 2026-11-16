/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.chatsettings.screens.sharablegrouplink

/**
 * Everything the group link screen reads off of the group's record. A group without a link of its own reads as a
 * disabled one with no url.
 */
data class GroupLink(
  val enabled: Boolean,
  val requiresAdminApproval: Boolean,
  val url: String,
  /** Whether the user is an admin of an active group, and so allowed to change the link at all. */
  val selfCanEditSettings: Boolean
) {

  companion object {
    /** What the screen shows until the group's link arrives: no link, and nothing the user can do about it. */
    val NONE = GroupLink(
      enabled = false,
      requiresAdminApproval = false,
      url = "",
      selfCanEditSettings = false
    )
  }
}
