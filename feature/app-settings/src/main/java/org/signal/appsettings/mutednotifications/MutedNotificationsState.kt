/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.mutednotifications

/**
 * What [MutedNotificationsScreen] renders: which notifications still come through while chats are muted, either for
 * every chat or for a single one.
 *
 * @param isGlobal whether these are the defaults for every muted chat, rather than one chat's own settings.
 * @param showMentions whether mentions apply, which they only do to groups and to the defaults.
 * @param showReplies whether replies apply, which they only do to groups and to the defaults.
 */
data class MutedNotificationsState(
  val isGlobal: Boolean = true,
  val allowCalls: Boolean = false,
  val showMentions: Boolean = true,
  val allowMentions: Boolean = false,
  val showReplies: Boolean = true,
  val allowReplies: Boolean = false
)
