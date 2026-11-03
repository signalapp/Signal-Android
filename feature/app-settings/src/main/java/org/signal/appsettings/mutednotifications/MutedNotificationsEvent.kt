/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.mutednotifications

/**
 * Everything that can happen on [MutedNotificationsScreen].
 */
sealed interface MutedNotificationsEvent {

  /** The toolbar's navigation icon was tapped. */
  data object NavigateBackClicked : MutedNotificationsEvent

  /** Calls were allowed or disallowed while muted. */
  data class CallsToggled(val allowCalls: Boolean) : MutedNotificationsEvent

  /** Mentions were allowed or disallowed while muted. */
  data class MentionsToggled(val allowMentions: Boolean) : MutedNotificationsEvent

  /** Replies were allowed or disallowed while muted. */
  data class RepliesToggled(val allowReplies: Boolean) : MutedNotificationsEvent
}
