package org.thoughtcrime.securesms.conversation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.database.MmsSmsColumns
import org.thoughtcrime.securesms.database.model.ThreadRecord

class ThreadCountTest {

  @Test
  fun `Given an Init, when I getCount, then I expect 0`() {
    // GIVEN
    val threadCount = ThreadCountAggregator.Init

    // WHEN
    val result = threadCount.count

    // THEN
    assertEquals(0, result)
  }

  @Test
  fun `Given an Empty, when I updateWith an outgoing record, then I expect Outgoing`() {
    // GIVEN
    val threadRecord = createThreadRecord(isOutgoing = true)

    // WHEN
    val result = ThreadCountAggregator.Init.updateWith(threadRecord)

    // THEN
    assertEquals(result, ThreadCountAggregator.Outgoing)
  }

  @Test
  fun `Given an Empty, when I updateWith an incoming record, then I expect 5`() {
    // GIVEN
    val threadRecord = createThreadRecord(unreadCount = 5)

    // WHEN
    val result = ThreadCountAggregator.Init.updateWith(threadRecord)

    // THEN
    assertEquals(5, result.count)
  }

  @Test
  fun `Given a Count, when I updateWith an incoming record with the same date, then I expect 5`() {
    // GIVEN
    val threadRecord = createThreadRecord(unreadCount = 5)
    val newThreadRecord = createThreadRecord(unreadCount = 1)

    // WHEN
    val result = ThreadCountAggregator.Init.updateWith(threadRecord).updateWith(newThreadRecord)

    // THEN
    assertEquals(5, result.count)
  }

  @Test
  fun `Given a Count, when I updateWith an incoming record with an earlier date, then I expect 5`() {
    // GIVEN
    val threadRecord = createThreadRecord(unreadCount = 5)
    val newThreadRecord = createThreadRecord(unreadCount = 1, date = 0L)

    // WHEN
    val result = ThreadCountAggregator.Init.updateWith(threadRecord).updateWith(newThreadRecord)

    // THEN
    assertEquals(5, result.count)
  }

  @Test
  fun `Given a Count, when I updateWith an incoming record with a later date, then I expect 6`() {
    // GIVEN
    val threadRecord = createThreadRecord(unreadCount = 5)
    val newThreadRecord = createThreadRecord(unreadCount = 1, date = 2L)

    // WHEN
    val result = ThreadCountAggregator.Init.updateWith(threadRecord).updateWith(newThreadRecord)

    // THEN
    assertEquals(6, result.count)
  }

  @Test
  fun `Given a Count, when I updateWith an incoming record with a later date and unread count gt 1, then I expect new unread count`() {
    // GIVEN
    val threadRecord = createThreadRecord(unreadCount = 5)
    val newThreadRecord = createThreadRecord(unreadCount = 3, date = 2L)

    // WHEN
    val result = ThreadCountAggregator.Init.updateWith(threadRecord).updateWith(newThreadRecord)

    // THEN
    assertEquals(3, result.count)
  }

  @Test
  fun `Given a Count, when I updateWith an incoming record with a different id, then I expect 3`() {
    // GIVEN
    val threadRecord = createThreadRecord(threadId = 1L, unreadCount = 5)
    val newThreadRecord = createThreadRecord(threadId = 2L, unreadCount = 3)

    // WHEN
    val result = ThreadCountAggregator.Init.updateWith(threadRecord).updateWith(newThreadRecord)

    // THEN
    assertEquals(3, result.count)
  }

  private fun createThreadRecord(threadId: Long = 1L, unreadCount: Int = 0, date: Long = 1L, isOutgoing: Boolean = false): ThreadRecord {
    val outgoingMessageType = MmsSmsColumns.Types.getOutgoingEncryptedMessageType()

    return ThreadRecord.Builder(threadId)
      .setUnreadCount(unreadCount)
      .setDate(date)
      .setType(if (isOutgoing) outgoingMessageType else (outgoingMessageType.inv()))
      .build()
  }
}
