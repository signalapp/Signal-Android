/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

/**
 * Describes how often a users messages are backed up.
 */
enum class MessageBackupsFrequency {
  DAILY,
  WEEKLY,
  MONTHLY,
  NEVER
}
