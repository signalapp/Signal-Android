/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.readToSingleBoolean
import org.signal.core.util.select
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageTableTest_markNotified {

  @get:Rule
  val recipients = RecipientTestRule()

  private val messages: MessageTable
    get() = SignalDatabase.messages

  @Test
  fun `markAsNotified marks only the given messages`() {
    val sender = recipients.createRecipient("Alice Bulk")
    val threadId = threadFor(sender)

    val first = insertIncoming(threadId, sender, time = 1000)
    val second = insertIncoming(threadId, sender, time = 1001)
    val untouched = insertIncoming(threadId, sender, time = 1002)

    messages.markAsNotified(listOf(first, second))

    assertThat(isNotified(first)).isTrue()
    assertThat(isNotified(second)).isTrue()
    assertThat(isNotified(untouched)).isFalse()
  }

  @Test
  fun `markAsNotified with no ids is a no-op`() {
    val sender = recipients.createRecipient("Bob Empty")
    val threadId = threadFor(sender)
    val message = insertIncoming(threadId, sender, time = 1000)

    messages.markAsNotified(emptyList())

    assertThat(isNotified(message)).isFalse()
  }

  @Test
  fun `markAsNotified marks every revision of an edited message`() {
    val sender = recipients.createRecipient("Carol Editor")
    val threadId = threadFor(sender)

    val original = insertIncoming(threadId, sender, time = 1000)
    val edit = insertEdit(sender, originalSentTimestamp = 1000, editSentTimeMillis = 1001)

    messages.markAsNotified(listOf(original))

    assertThat(isNotified(original)).isTrue()
    assertThat(isNotified(edit)).isTrue()
  }

  @Test
  fun `markConversationsAsNotified marks unnotified messages up to the bound`() {
    val sender = recipients.createRecipient("Dave Bound")
    val threadId = threadFor(sender)

    val first = insertIncoming(threadId, sender, time = 1000)
    val second = insertIncoming(threadId, sender, time = 1001)

    messages.markConversationsAsNotified(listOf(ConversationId.forConversation(threadId)), second)

    assertThat(isNotified(first)).isTrue()
    assertThat(isNotified(second)).isTrue()
  }

  @Test
  fun `markConversationsAsNotified leaves messages above the bound alone`() {
    val sender = recipients.createRecipient("Erin Snapshot")
    val threadId = threadFor(sender)

    val inSnapshot = insertIncoming(threadId, sender, time = 1000)
    val arrivedAfter = insertIncoming(threadId, sender, time = 1001)

    messages.markConversationsAsNotified(listOf(ConversationId.forConversation(threadId)), inSnapshot)

    assertThat(isNotified(inSnapshot)).isTrue()
    assertThat(isNotified(arrivedAfter)).isFalse()
  }

  @Test
  fun `markConversationsAsNotified bounds by id and not by received time`() {
    val sender = recipients.createRecipient("Frank Backdated")
    val threadId = threadFor(sender)

    val inSnapshot = insertIncoming(threadId, sender, time = 5000)
    val backdated = insertIncoming(threadId, sender, time = 1000)

    messages.markConversationsAsNotified(listOf(ConversationId.forConversation(threadId)), inSnapshot)

    assertThat(isNotified(inSnapshot)).isTrue()
    assertThat(isNotified(backdated)).isFalse()
  }

  @Test
  fun `markConversationsAsNotified only touches the given threads`() {
    val included = recipients.createRecipient("Grace Included")
    val excluded = recipients.createRecipient("Heidi Excluded")
    val includedThreadId = threadFor(included)
    val excludedThreadId = threadFor(excluded)

    val includedMessage = insertIncoming(includedThreadId, included, time = 1000)
    val excludedMessage = insertIncoming(excludedThreadId, excluded, time = 1001)

    messages.markConversationsAsNotified(listOf(ConversationId.forConversation(includedThreadId)), excludedMessage)

    assertThat(isNotified(includedMessage)).isTrue()
    assertThat(isNotified(excludedMessage)).isFalse()
  }

  @Test
  fun `markConversationsAsNotified with no conversations is a no-op`() {
    val sender = recipients.createRecipient("Ivan None")
    val threadId = threadFor(sender)
    val message = insertIncoming(threadId, sender, time = 1000)

    messages.markConversationsAsNotified(emptyList(), message)

    assertThat(isNotified(message)).isFalse()
  }

  @Test
  fun `markConversationsAsNotified with a non-positive bound is a no-op`() {
    val sender = recipients.createRecipient("Judy Zero")
    val threadId = threadFor(sender)
    val message = insertIncoming(threadId, sender, time = 1000)

    messages.markConversationsAsNotified(listOf(ConversationId.forConversation(threadId)), 0)

    assertThat(isNotified(message)).isFalse()
  }

  @Test
  fun `markConversationsAsNotified for a group story reply leaves the main chat alone`() {
    val sender = recipients.createRecipient("Karl Story")
    val group = recipients.createGroup(sender)
    val threadId = threadFor(group.recipientId)

    val storyId = insertIncoming(threadId, sender, time = 1000)
    val chatMessage = insertIncoming(threadId, sender, time = 1001)
    val reply = insertGroupReply(threadId, sender, time = 1002, parentStoryId = storyId)

    messages.markConversationsAsNotified(listOf(ConversationId(threadId, storyId)), reply)

    assertThat(isNotified(reply)).isTrue()
    assertThat(isNotified(chatMessage)).isFalse()
  }

  @Test
  fun `markConversationsAsNotified for a chat leaves group story replies alone`() {
    val sender = recipients.createRecipient("Lena Chat")
    val group = recipients.createGroup(sender)
    val threadId = threadFor(group.recipientId)

    val storyId = insertIncoming(threadId, sender, time = 1000)
    val chatMessage = insertIncoming(threadId, sender, time = 1001)
    val reply = insertGroupReply(threadId, sender, time = 1002, parentStoryId = storyId)

    messages.markConversationsAsNotified(listOf(ConversationId.forConversation(threadId)), reply)

    assertThat(isNotified(chatMessage)).isTrue()
    assertThat(isNotified(reply)).isFalse()
  }

  private fun threadFor(recipientId: RecipientId): Long {
    return SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
  }

  private fun insertIncoming(threadId: Long, from: RecipientId, time: Long): Long {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      sentTimeMillis = time,
      serverTimeMillis = time,
      receivedTimeMillis = time,
      body = "msg $time"
    )
    return messages.insertMessageInbox(message, threadId).get().messageId
  }

  private fun insertGroupReply(threadId: Long, from: RecipientId, time: Long, parentStoryId: Long): Long {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      sentTimeMillis = time,
      serverTimeMillis = time,
      receivedTimeMillis = time,
      body = "reply $time",
      parentStoryId = ParentStoryId.GroupReply(parentStoryId)
    )
    return messages.insertMessageInbox(message, threadId).get().messageId
  }

  private fun insertEdit(from: RecipientId, originalSentTimestamp: Long, editSentTimeMillis: Long): Long {
    val target = messages.getMessageFor(originalSentTimestamp, from) as MmsMessageRecord
    val edit = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      sentTimeMillis = editSentTimeMillis,
      serverTimeMillis = editSentTimeMillis,
      receivedTimeMillis = editSentTimeMillis,
      body = "edited at $editSentTimeMillis"
    )
    return messages.insertEditMessageInbox(edit, target).get().messageId
  }

  private fun isNotified(messageId: Long): Boolean {
    return SignalDatabase.writableDatabase
      .select(MessageTable.NOTIFIED)
      .from(MessageTable.TABLE_NAME)
      .where("${MessageTable.ID} = ?", messageId)
      .run()
      .readToSingleBoolean()
  }
}
