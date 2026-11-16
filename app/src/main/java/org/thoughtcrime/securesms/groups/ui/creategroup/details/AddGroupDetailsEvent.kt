/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import org.signal.core.models.media.Media
import org.signal.core.models.recipients.RecipientId

sealed interface AddGroupDetailsEvent {

  data object NavigateBackClicked : AddGroupDetailsEvent

  data class NameChanged(val name: String) : AddGroupDetailsEvent {
    override fun toString(): String = "NameChanged"
  }

  data object AvatarClicked : AddGroupDetailsEvent

  data class AvatarSelected(val media: Media) : AddGroupDetailsEvent {
    override fun toString(): String = "AvatarSelected"
  }

  data object AvatarCleared : AddGroupDetailsEvent

  data object DisappearingMessagesClicked : AddGroupDetailsEvent

  data class DisappearingMessagesTimerSelected(val timer: Int) : AddGroupDetailsEvent {
    override fun toString(): String = "Timer"
  }

  data class MemberClicked(val recipientId: RecipientId) : AddGroupDetailsEvent

  data object RemoveMemberConfirmed : AddGroupDetailsEvent

  data class SameGroupClicked(val groupId: RecipientId) : AddGroupDetailsEvent

  data object DiscardConfirmed : AddGroupDetailsEvent

  data object DialogDismissed : AddGroupDetailsEvent

  data object CreateClicked : AddGroupDetailsEvent
}
