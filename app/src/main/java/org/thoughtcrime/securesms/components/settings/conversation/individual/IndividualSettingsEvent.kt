/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.individual

import org.signal.uicomponents.recentmediarail.RecentMediaRailAction
import org.signal.uicomponents.recentmediarail.RecentMediaRailEvents
import org.signal.uicomponents.recentmediarail.RecentMediaRailState
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallEntry
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface IndividualSettingsEvent {

  /** The user tapped the header avatar, which opens the avatar preview or the recipient's story. */
  data object AvatarClicked : IndividualSettingsEvent

  /** The user tapped one of the badges shown under the recipient's name. */
  data class BadgeClicked(val badge: Badge) : IndividualSettingsEvent {
    override fun toString(): String = "BadgeClicked"
  }

  /** The user tapped the recipient's name, which opens the about sheet. */
  data object HeadlineClicked : IndividualSettingsEvent

  /** The user tapped the internal details button, which only internal users ever see. */
  data object InternalDetailsClicked : IndividualSettingsEvent

  /** The user tapped the message button, which opens the conversation. */
  data object MessageClicked : IndividualSettingsEvent

  /** The user tapped the video call button. */
  data object VideoCallClicked : IndividualSettingsEvent

  /** The user tapped the audio call button. */
  data object AudioCallClicked : IndividualSettingsEvent

  /** The user tapped the mute button, which opens the mute menu or asks to confirm unmuting. */
  data object MuteClicked : IndividualSettingsEvent

  /** The user picked one of the preset durations out of the mute menu. */
  data class MuteDurationSelected(val muteUntil: Long) : IndividualSettingsEvent

  /** The user asked to mute until a time of their own choosing, which the fragment prompts for. */
  data object MuteUntilCustomTimeClicked : IndividualSettingsEvent

  /** The user confirmed unmuting the chat. */
  data object UnmuteConfirmed : IndividualSettingsEvent

  /** The user tapped the search button, which opens the conversation with search already running. */
  data object SearchClicked : IndividualSettingsEvent

  /** The user tapped the disappearing messages row. */
  data object DisappearingMessagesClicked : IndividualSettingsEvent

  /** The user tapped the nickname row. */
  data object NicknameClicked : IndividualSettingsEvent

  /** The user tapped the chat color and wallpaper row. */
  data object ChatColorAndWallpaperClicked : IndividualSettingsEvent

  /** The user tapped the sounds and notifications row. */
  data object SoundsAndNotificationsClicked : IndividualSettingsEvent

  /** The user tapped the starred messages row. */
  data object StarredMessagesClicked : IndividualSettingsEvent

  /** The user tapped the row that opens the recipient's entry in the system contacts. */
  data object ContactDetailsClicked : IndividualSettingsEvent

  /** The user tapped the row that adds the recipient to the system contacts. */
  data object AddAsContactClicked : IndividualSettingsEvent

  /** The user tapped the safety number row. */
  data object ViewSafetyNumberClicked : IndividualSettingsEvent

  /** The user tapped the support center link, which only the release notes chat offers. */
  data object SupportCenterClicked : IndividualSettingsEvent

  /** The user tapped the contact us link, which only the release notes chat offers. */
  data object ContactUsClicked : IndividualSettingsEvent

  /** The user tapped the donate link, which only the release notes chat offers. */
  data object DonateClicked : IndividualSettingsEvent

  /** The user tapped the row that adds this recipient to one of their groups. */
  data object AddToAGroupClicked : IndividualSettingsEvent

  /** The user tapped one of the groups they have in common with the recipient. */
  data class GroupInCommonClicked(val recipientId: RecipientId) : IndividualSettingsEvent

  /** The user tapped "see all" under the collapsed list of groups in common. */
  data object RevealAllGroupsInCommonClicked : IndividualSettingsEvent

  /** The user tapped block or unblock, which asks the fragment to confirm first. */
  data object BlockClicked : IndividualSettingsEvent

  /** The user confirmed the block in the fragment's dialog. */
  data object BlockConfirmed : IndividualSettingsEvent

  /** The user confirmed the unblock in the fragment's dialog. */
  data object UnblockConfirmed : IndividualSettingsEvent

  /** The user tapped report spam, which asks the fragment to confirm first. */
  data object ReportSpamClicked : IndividualSettingsEvent

  /** The user confirmed reporting spam without also blocking. */
  data object ReportSpamConfirmed : IndividualSettingsEvent

  /** The user confirmed reporting spam and blocking in the same step. */
  data object BlockAndReportSpamConfirmed : IndividualSettingsEvent

  /** Dismisses whatever is in [IndividualSettingsState.dialog]. */
  data object DialogDismissed : IndividualSettingsEvent

  /** The user came back from adding or viewing a system contact, so the recipient may be out of date. */
  data object RecipientRefreshRequested : IndividualSettingsEvent

  /** The recipient this screen is about changed. */
  data class RecipientChanged(val recipient: Recipient) : IndividualSettingsEvent {
    override fun toString(): String = "RecipientChanged(${recipient.id})"
  }

  /** The recipient's story became unviewed, viewed, or went away entirely. */
  data class StoryViewStateChanged(val storyViewState: StoryViewState) : IndividualSettingsEvent

  /** The calls behind the call info variant of this screen finished loading. */
  data class CallsChanged(val calls: List<CallEntry>) : IndividualSettingsEvent {
    override fun toString(): String = "CallsChanged(count=${calls.size})"
  }

  /** The recipient's thread id came back, or -1 if they don't have a thread yet. */
  data class ThreadIdLoaded(val threadId: Long) : IndividualSettingsEvent

  /** The groups the user and this recipient are both in changed. */
  data class GroupsInCommonChanged(val groupsInCommon: List<Recipient>) : IndividualSettingsEvent {
    override fun toString(): String = "GroupsInCommonChanged(count=${groupsInCommon.size})"
  }

  /** Whether the user is in any groups at all came back, which decides if we can offer to add this recipient to one. */
  data class SelfHasGroupsLoaded(val selfHasGroups: Boolean) : IndividualSettingsEvent

  /** The recipient's identity record came back, which the safety number row needs. */
  data class IdentityRecordLoaded(val identityRecord: IdentityRecord?) : IndividualSettingsEvent {
    override fun toString(): String = "IdentityRecordLoaded(present=${identityRecord != null})"
  }

  /** Received an event from the media rail that we want to forward */
  data class MediaRailEvent(val event: RecentMediaRailEvents) : IndividualSettingsEvent

  /** The media rail's presenter emitted new state for us to mirror. */
  data class MediaRailStateChanged(val railState: RecentMediaRailState) : IndividualSettingsEvent {
    override fun toString(): String = "MediaRailStateChanged(count=${railState.media.size}, loaded=${railState.loaded})"
  }

  /** The media rail's presenter decided something needs doing that only this screen can do. */
  data class MediaRailAction(val action: RecentMediaRailAction) : IndividualSettingsEvent
}
