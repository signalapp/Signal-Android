/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Used in [AddGroupDetailsScreen]
 */
data class AddGroupDetailsState(
  val members: List<Recipient> = emptyList(),
  val name: String = "",
  val avatar: ByteArray? = null,
  val disappearingMessagesTimer: Int = 0,
  val sameGroups: List<Recipient> = emptyList(),
  val isCreating: Boolean = false,
  val dialog: Dialog = Dialog.None
) {
  val canCreate: Boolean
    get() = !isCreating && name.isNotBlank()

  override fun toString(): String {
    return "AddGroupDetailsState(members=${members.size}, hasAvatar=${avatar != null}, sameGroups=${sameGroups.size}, isCreating=$isCreating, dialog=$dialog)"
  }

  sealed interface Dialog {
    data object None : Dialog

    data class ConfirmRemoveMember(val member: Recipient) : Dialog {
      override fun toString(): String = "ConfirmRemoveMember(member=${member.id})"
    }

    data class ConfirmDiscard(val group: Recipient) : Dialog {
      override fun toString(): String = "ConfirmDiscard(group=${group.id})"
    }
  }
}
