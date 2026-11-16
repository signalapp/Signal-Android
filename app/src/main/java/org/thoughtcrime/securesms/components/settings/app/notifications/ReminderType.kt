/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications

/**
 * Tracks the type of reminders used when constructing the periodic unread reminder notification. See [org.thoughtcrime.securesms.jobs.UnreadReminderJob]
 */
enum class ReminderType {
  MESSAGES,
  MENTIONS,
  REPLIES
}
