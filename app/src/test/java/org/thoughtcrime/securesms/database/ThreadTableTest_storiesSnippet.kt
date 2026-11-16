/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule

/**
 * Verifies that a thread which cannot be deleted because it still holds stories does not
 * keep showing a snippet for a message that no longer exists (e.g. after it expired).
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ThreadTableTest_storiesSnippet {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var recipient: RecipientId
  private val allChats: ChatFolderRecord = ChatFolderRecord(folderType = ChatFolderRecord.FolderType.ALL)

  @Before
  fun setUp() {
    recipient = recipients.createRecipient("Alice Android")
  }

  @Test
  fun `clears snippet and hides unpinned thread with a story when its last message is deleted`() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    val messageId = recipients.insertOutgoingMessage(recipient, body = "disappearing message")
    SignalDatabase.threads.update(threadId, false)
    insertIncomingStory(from = recipient, sentTimeMillis = System.currentTimeMillis())

    assertEquals("disappearing message", SignalDatabase.threads.getThreadRecord(threadId)!!.snippet)
    assertEquals(1, SignalDatabase.threads.getUnarchivedConversationListCount(ConversationFilter.OFF, allChats))

    // WHEN
    val threadDeleted = SignalDatabase.messages.deleteMessage(messageId)

    // THEN
    assertFalse(threadDeleted)
    val record = SignalDatabase.threads.getThreadRecord(threadId)
    assertNotNull(record)
    assertNull(record!!.snippet)
    assertFalse(record.meaningfulMessages)
    assertEquals(0, record.snippetMessageId)
    assertEquals(0, SignalDatabase.threads.getUnarchivedConversationListCount(ConversationFilter.OFF, allChats))
    assertTrue(SignalDatabase.messages.containsStories(threadId))
  }

  @Test
  fun `deletes unpinned thread without stories when its last message is deleted`() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    val messageId = recipients.insertOutgoingMessage(recipient)

    // WHEN
    val threadDeleted = SignalDatabase.messages.deleteMessage(messageId)

    // THEN
    assertTrue(threadDeleted)
    assertFalse(SignalDatabase.threads.getThreadRecord(threadId)?.active ?: false)
    assertEquals(0, SignalDatabase.threads.getUnarchivedConversationListCount(ConversationFilter.OFF, allChats))
  }

  @Test
  fun `leaves snippet alone for thread with a story when deletion is not allowed`() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    val messageId = recipients.insertOutgoingMessage(recipient, body = "disappearing message")
    SignalDatabase.threads.update(threadId, false)
    insertIncomingStory(from = recipient, sentTimeMillis = System.currentTimeMillis())
    SignalDatabase.messages.deleteMessage(messageId, threadId, notify = false, updateThread = false)

    // WHEN
    SignalDatabase.threads.update(threadId, unarchive = false, allowDeletion = false)

    // THEN
    assertEquals("disappearing message", SignalDatabase.threads.getThreadRecord(threadId)!!.snippet)
  }

  @Test
  fun `does not report group thread as deleted when its only story reply is deleted`() {
    // GIVEN
    val group = recipients.createGroup(recipient)
    val groupRecipient = Recipient.resolved(group.recipientId)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(groupRecipient)
    val storyId = insertOutgoingMessage(groupRecipient, threadId, storyType = StoryType.STORY_WITH_REPLIES)
    val replyId = insertOutgoingMessage(groupRecipient, threadId, parentStoryId = ParentStoryId.GroupReply(storyId))

    // WHEN
    val threadDeleted = SignalDatabase.messages.deleteMessage(replyId)

    // THEN
    assertFalse(threadDeleted)
    assertNotNull(SignalDatabase.threads.getThreadRecord(threadId))
    assertTrue(SignalDatabase.messages.containsStories(threadId))
  }

  private fun insertOutgoingMessage(
    recipient: Recipient,
    threadId: Long,
    storyType: StoryType = StoryType.NONE,
    parentStoryId: ParentStoryId? = null
  ): Long {
    val message = OutgoingMessage(
      recipient = recipient,
      body = "body",
      timestamp = System.currentTimeMillis(),
      storyType = storyType,
      parentStoryId = parentStoryId,
      isSecure = true
    )
    return recipients.insertOutgoingMessage(message, threadId)
  }

  private fun insertIncomingStory(from: RecipientId, sentTimeMillis: Long) {
    SignalDatabase.messages.insertMessageInbox(
      IncomingMessage(
        type = MessageType.NORMAL,
        from = from,
        sentTimeMillis = sentTimeMillis,
        serverTimeMillis = sentTimeMillis,
        receivedTimeMillis = sentTimeMillis,
        storyType = StoryType.STORY_WITH_REPLIES
      ),
      -1L
    )
  }
}
