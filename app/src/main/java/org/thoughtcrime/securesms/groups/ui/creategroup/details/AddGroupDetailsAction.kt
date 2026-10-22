/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import org.signal.core.models.media.Media
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * One-shot side effects that have to be carried out by the host.
 */
sealed interface AddGroupDetailsAction {

  data object NavigateBack : AddGroupDetailsAction

  data class NavigateToAvatarPicker(val currentMedia: Media?) : AddGroupDetailsAction {
    override fun toString(): String = "NavigateToAvatarPicker(hasMedia=${currentMedia != null})"
  }

  data class LaunchDisappearingMessagesPicker(val timer: Int) : AddGroupDetailsAction

  data class OpenConversation(val groupId: RecipientId, val threadId: Long) : AddGroupDetailsAction

  data class GroupCreated(val groupId: RecipientId, val threadId: Long, val invitedMembers: List<Recipient>) : AddGroupDetailsAction {
    override fun toString(): String = "GroupCreated(groupId=$groupId, threadId=$threadId, invitedMembers=${invitedMembers.size})"
  }

  data object ShowTryAgainLater : AddGroupDetailsAction

  data object ShowGroupCreationFailed : AddGroupDetailsAction
}
