/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.polls

import org.signal.core.models.recipients.RecipientId

/**
 * Tracks general information of a poll vote including who they are and what poll they voted in. Primarily used in notifications.
 */
data class PollVote(
  val pollId: Long,
  val voterId: RecipientId,
  val question: String,
  val dateReceived: Long = 0
)
