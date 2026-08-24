/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.data

import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.MessageConstraintsUtil

/**
 * An in-memory cache of messages that were recently deleted locally via "delete for me" but are still eligible for a remote delete.
 * Contains only the id's and basic metadata needed to send a remote delete (i.e. no message contents).
 */
object DeletedMessageTombstoneCache {

  private val entriesByThread: MutableMap<Long, MutableList<Entry>> = HashMap()

  /**
   * Caches a tombstone for the given record if it is still eligible for a remote delete.
   * Should be called before the record is deleted from the database.
   */
  @JvmStatic
  @Synchronized
  fun add(record: MessageRecord) {
    if (!SignalStore.labs.improvedMessageDeletion) {
      return
    }

    if (record.toRecipient.isSelf || !MessageConstraintsUtil.isValidRemoteDeleteSend(record, System.currentTimeMillis())) {
      return
    }

    val entries = entriesByThread.getOrPut(record.threadId) { mutableListOf() }
    entries.removeIf { it.messageId == record.id }
    entries += Entry(
      messageId = record.id,
      threadId = record.threadId,
      toRecipientId = record.toRecipient.id,
      dateSent = record.dateSent,
      dateReceived = record.dateReceived,
      type = record.type,
      expiresIn = record.expiresIn,
      expireStarted = record.expireStarted,
      expireTimerVersion = record.expireTimerVersion
    )
  }

  /**
   * Returns all still-valid tombstones for the given thread, ordered by date received descending,
   * matching the sort order of the conversation query.
   */
  @JvmStatic
  @Synchronized
  fun getForThread(threadId: Long): List<Entry> {
    if (!SignalStore.labs.improvedMessageDeletion) {
      return emptyList()
    }

    val entries = entriesByThread[threadId] ?: return emptyList()
    val currentTime = System.currentTimeMillis()

    entries.removeIf { !MessageConstraintsUtil.isValidRemoteDeleteSend(it.dateSent, currentTime) }

    return entries.sortedByDescending { it.dateReceived }
  }

  /**
   * Returns all still-valid tombstones for the given thread whose [Entry.dateReceived] falls within the
   * half-open range (exclusive [minDateReceivedExclusive], inclusive [maxDateReceivedInclusive]], ordered by
   * date received descending. The half-open range lets adjacent conversation pages tile without gaps or overlap,
   * so every tombstone lands on exactly one page.
   */
  @JvmStatic
  @Synchronized
  fun getForThread(threadId: Long, minDateReceivedExclusive: Long, maxDateReceivedInclusive: Long): List<Entry> {
    return getForThread(threadId).filter { it.dateReceived > minDateReceivedExclusive && it.dateReceived <= maxDateReceivedInclusive }
  }

  @JvmStatic
  @Synchronized
  fun remove(threadId: Long, messageId: Long) {
    entriesByThread[threadId]?.removeIf { it.messageId == messageId }
  }

  /**
   * Clears all tombstones for the given thread. Called when the user leaves the conversation, since
   * tombstones are only meant to live for the duration of a visit to the chat.
   */
  @JvmStatic
  @Synchronized
  fun clearThread(threadId: Long) {
    entriesByThread.remove(threadId)
  }

  data class Entry(
    val messageId: Long,
    val threadId: Long,
    val toRecipientId: RecipientId,
    val dateSent: Long,
    val dateReceived: Long,
    val type: Long,
    val expiresIn: Long,
    val expireStarted: Long,
    val expireTimerVersion: Int
  )
}
