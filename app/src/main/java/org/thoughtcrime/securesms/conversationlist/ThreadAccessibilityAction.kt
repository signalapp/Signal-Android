/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist

/**
 * Accessibility actions that can be performed on conversation list threads.
 */
enum class ThreadAccessibilityAction {
  MARK_AS_READ,
  MARK_AS_UNREAD,
  PIN,
  UNPIN,
  MUTE,
  UNMUTE,
  SELECT,
  ARCHIVE,
  UNARCHIVE,
  DELETE
}
