/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.database.model

import android.net.Uri
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Represents a single row in the thread table.
 */
data class ThreadRecord(
  val threadId: Long,
  val date: Long,
  val meaningfulMessages: Boolean,
  val recipientId: RecipientId,
  val read: ThreadTable.ReadStatus,
  val type: Long,
  val error: Int,
  val snippet: String?,
  val snippetType: Long,
  val snippetUri: Uri?,
  val snippetContentType: String?,
  val snippetExtras: ThreadTable.Extra?,
  val unreadCount: Int,
  val archived: Boolean,
  val status: Long,
  val hasDeliveryReceipt: Boolean,
  val hasReadReceipt: Boolean,
  val expiresIn: Long,
  val lastSeen: Long,
  val hasSent: Boolean,
  val lastScrolled: Long,
  val pinnedOrder: Long?,
  val unreadSelfMentionCount: Int,
  val active: Boolean,
  val snippetMessageExtras: MessageExtras?,
  val snippetMessageId: Long
)
