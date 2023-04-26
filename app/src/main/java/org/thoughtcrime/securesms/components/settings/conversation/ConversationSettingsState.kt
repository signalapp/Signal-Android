package org.thoughtcrime.securesms.components.settings.conversation

import android.database.Cursor
import org.thoughtcrime.securesms.components.settings.conversation.preferences.ButtonStripPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.CallPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.LegacyGroupPreference
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry
import org.thoughtcrime.securesms.recipients.Recipient

data class ConversationSettingsState(
  val threadId: Long = -1,
  val storyViewState: StoryViewState = StoryViewState.NONE,
  val recipient: Recipient = Recipient.UNKNOWN,
  val buttonStripState: ButtonStripPreference.State = ButtonStripPreference.State(),
  val disappearingMessagesLifespan: Int = 0,
  val canModifyBlockedState: Boolean = false,
  val sharedMedia: Cursor? = null,
  val sharedMediaIds: List<Long> = listOf(),
  val displayInternalRecipientDetails: Boolean = false,
  val calls: List<CallPreference.Model> = emptyList(),
  private val sharedMediaLoaded: Boolean = false,
  private val specificSettingsState: SpecificSettingsState
) {

  val isLoaded: Boolean = recipient != Recipient.UNKNOWN && sharedMediaLoaded && specificSettingsState.isLoaded

  fun withRecipientSettingsState(consumer: (SpecificSettingsState.RecipientSettingsState) -> Unit) {
    if (specificSettingsState is SpecificSettingsState.RecipientSettingsState) {
      consumer(specificSettingsState)
    }
  }

  fun withGroupSettingsState(consumer: (SpecificSettingsState.GroupSettingsState) -> Unit) {
    if (specificSettingsState is SpecificSettingsState.GroupSettingsState) {
      consumer(specificSettingsState)
    }
  }

  fun requireRecipientSettingsState(): SpecificSettingsState.RecipientSettingsState = specificSettingsState.requireRecipientSettingsState()
  fun requireGroupSettingsState(): SpecificSettingsState.GroupSettingsState = specificSettingsState.requireGroupSettingsState()
}

sealed class SpecificSettingsState {

  abstract val isLoaded: Boolean

  data class RecipientSettingsState(
    val identityRecord: IdentityRecord? = null,
    val allGroupsInCommon: List<Recipient> = listOf(),
    val groupsInCommon: List<Recipient> = listOf(),
    val selfHasGroups: Boolean = false,
    val canShowMoreGroupsInCommon: Boolean = false,
    val groupsInCommonExpanded: Boolean = false,
    val contactLinkState: ContactLinkState = ContactLinkState.NONE
  ) : SpecificSettingsState() {

    override val isLoaded: Boolean = true

    override fun requireRecipientSettingsState() = this
  }

  data class GroupSettingsState(
    val groupId: GroupId,
    val allMembers: List<GroupMemberEntry.FullMember> = listOf(),
    val members: List<GroupMemberEntry.FullMember> = listOf(),
    val isSelfAdmin: Boolean = false,
    val canAddToGroup: Boolean = false,
    val canEditGroupAttributes: Boolean = false,
    val canLeave: Boolean = false,
    val canShowMoreGroupMembers: Boolean = false,
    val groupMembersExpanded: Boolean = false,
    val groupTitle: String = "",
    private val groupTitleLoaded: Boolean = false,
    val groupDescription: String? = null,
    val groupDescriptionShouldLinkify: Boolean = false,
    private val groupDescriptionLoaded: Boolean = false,
    val groupLinkEnabled: Boolean = false,
    val membershipCountDescription: String = "",
    val legacyGroupState: LegacyGroupPreference.State = LegacyGroupPreference.State.NONE,
    val isAnnouncementGroup: Boolean = false
  ) : SpecificSettingsState() {

    override val isLoaded: Boolean = groupTitleLoaded && groupDescriptionLoaded

    override fun requireGroupSettingsState(): GroupSettingsState = this
  }

  open fun requireRecipientSettingsState(): RecipientSettingsState = error("Not a recipient settings state")
  open fun requireGroupSettingsState(): GroupSettingsState = error("Not a group settings state")
}

enum class ContactLinkState {
  OPEN,
  ADD,
  NONE
}
