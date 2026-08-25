/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.individual

import org.signal.uicomponents.recentmediarail.RecentMediaRailState
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBarState
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallEntry
import org.thoughtcrime.securesms.components.settings.conversation.shared.CollapsibleList
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.recipients.Recipient

data class IndividualSettingsState(
  val recipient: Recipient = Recipient.UNKNOWN,
  /** Changed when recipient content changes, since Recipient.equals only compares IDs. */
  val recipientContentVersion: Int = 0,
  val threadId: Long = -1L,
  val storyViewState: StoryViewState = StoryViewState.NONE,
  val isDeprecatedOrUnregistered: Boolean = false,
  val displayInternalRecipientDetails: Boolean = false,
  val starredMessagesEnabled: Boolean = false,
  val disappearingMessagesLifespan: Int = 0,
  val canModifyBlockedState: Boolean = false,
  val identityRecord: IdentityRecord? = null,
  val contactLinkState: ContactLinkState = ContactLinkState.NONE,
  val allGroupsInCommon: List<Recipient> = emptyList(),
  val selfHasGroups: Boolean = false,
  val groupsInCommonExpanded: Boolean = false,
  val mediaRail: RecentMediaRailState = RecentMediaRailState(),
  val calls: List<CallEntry> = emptyList(),
  val callBar: CallBarState = CallBarState(),
  val dialog: Dialog = Dialog.None
) {

  /**
   * True once we've loaded enough to render the screen without it visibly shuffling around. Shared media is
   * deliberately not part of this: the rail reserves its space while loading, so there's no reason to hold the whole
   * screen behind it.
   */
  val isLoaded: Boolean = recipient != Recipient.UNKNOWN

  val groupsInCommon: List<Recipient> = CollapsibleList.collapse(allGroupsInCommon, groupsInCommonExpanded)

  val canShowMoreGroupsInCommon: Boolean = CollapsibleList.canExpand(allGroupsInCommon, groupsInCommonExpanded)

  sealed interface Dialog {
    data object None : Dialog
    data object MuteMenu : Dialog
    data object Unmute : Dialog
  }
}

/** Whether we can offer to open the recipient's system contact entry, offer to create one, or neither. */
enum class ContactLinkState {
  OPEN,
  ADD,
  NONE
}
