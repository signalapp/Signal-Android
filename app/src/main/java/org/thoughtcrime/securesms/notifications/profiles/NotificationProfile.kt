package org.thoughtcrime.securesms.notifications.profiles

import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.recipients.RecipientId

data class NotificationProfile(
  val id: Long,
  val name: String,
  val emoji: String,
  val color: AvatarColor = AvatarColor.A210,
  val createdAt: Long,
  val allowAllCalls: Boolean = true,
  val allowAllMentions: Boolean = false,
  val schedule: NotificationProfileSchedule,
  val allowedMembers: Set<RecipientId> = emptySet()
) : Comparable<NotificationProfile> {

  fun isRecipientAllowed(id: RecipientId): Boolean {
    return allowedMembers.contains(id)
  }

  override fun compareTo(other: NotificationProfile): Int {
    return createdAt.compareTo(other.createdAt)
  }
}
