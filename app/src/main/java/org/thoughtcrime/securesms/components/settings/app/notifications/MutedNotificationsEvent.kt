package org.thoughtcrime.securesms.components.settings.app.notifications

/**
 * Events emitted by [MutedNotificationScreen] and handled by [MutedNotificationsViewModel]
 */
sealed interface MutedNotificationsEvent {
  data class CallsToggled(val allowCalls: Boolean) : MutedNotificationsEvent
  data class MentionsToggled(val allowMentions: Boolean) : MutedNotificationsEvent
  data class RepliesToggled(val allowReplies: Boolean) : MutedNotificationsEvent
}
