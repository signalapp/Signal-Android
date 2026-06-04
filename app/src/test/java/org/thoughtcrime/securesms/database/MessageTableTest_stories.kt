/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import io.mockk.every
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import java.util.Optional
import java.util.concurrent.TimeUnit

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageTableTest_stories {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var mms: MessageTable

  private lateinit var myStory: Recipient
  private lateinit var others: List<RecipientId>
  private lateinit var releaseChannelRecipient: Recipient

  @Before
  fun setUp() {
    mms = SignalDatabase.messages

    myStory = Recipient.resolved(SignalDatabase.recipients.getOrInsertFromDistributionListId(DistributionListId.MY_STORY))
    others = (0 until 5).map { recipients.createRecipient("Other $it") }
    releaseChannelRecipient = Recipient.resolved(SignalDatabase.recipients.insertReleaseChannelRecipient())

    every { recipients.signalStore.releaseChannel.releaseChannelRecipientId } returns releaseChannelRecipient.id
  }

  @Test
  fun givenNoStories_whenIGetOrderedStoryRecipientsAndIds_thenIExpectAnEmptyList() {
    // WHEN
    val result = mms.getOrderedStoryRecipientsAndIds(false)

    // THEN
    assertEquals(0, result.size)
  }

  @Test
  fun givenOneOutgoingAndOneIncomingStory_whenIGetOrderedStoryRecipientsAndIds_thenIExpectIncomingThenOutgoing() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(myStory)
    val sender = others[0]

    insertOutgoingStory(recipient = myStory, sentTimeMillis = 1, threadId = threadId)
    insertIncomingStory(from = sender, sentTimeMillis = 2)

    // WHEN
    val result = mms.getOrderedStoryRecipientsAndIds(false)

    // THEN
    assertEquals(listOf(sender.toLong(), myStory.id.toLong()), result.map { it.recipientId.toLong() })
  }

  @Test
  fun givenAStory_whenISetIncomingStoryMessageViewed_thenIExpectASetReceiptTimestamp() {
    // GIVEN
    val sender = others[0]
    val messageId = insertIncomingStory(from = sender, sentTimeMillis = 2).get().messageId

    val messageBeforeMark = mms.getMessageRecord(messageId)
    assertFalse(messageBeforeMark.incomingStoryViewedAtTimestamp > 0)

    // WHEN
    mms.setIncomingMessageViewed(messageId)

    // THEN
    val messageAfterMark = mms.getMessageRecord(messageId)
    assertTrue(messageAfterMark.incomingStoryViewedAtTimestamp > 0)
  }

  @Test
  fun given5ViewedStories_whenIGetOrderedStoryRecipientsAndIds_thenIExpectLatestViewedFirst() {
    // GIVEN
    val messageIds = others.take(5).map {
      insertIncomingStory(from = it, sentTimeMillis = 2).get().messageId
    }

    val randomizedOrderedIds = messageIds.shuffled()
    randomizedOrderedIds.forEach {
      mms.setIncomingMessageViewed(it)
      Thread.sleep(5)
    }

    // WHEN
    val result = mms.getOrderedStoryRecipientsAndIds(false)
    val resultOrderedIds = result.map { it.messageId }

    // THEN
    assertEquals(randomizedOrderedIds.reversed(), resultOrderedIds)
  }

  @Test
  fun given15Stories_whenIGetOrderedStoryRecipientsAndIds_thenIExpectUnviewedThenInterspersedViewedAndSelfSendsAllDescending() {
    val myStoryThread = SignalDatabase.threads.getOrCreateThreadIdFor(myStory)

    val unviewedIds: List<Long> = (0 until 5).map {
      Thread.sleep(5)
      insertIncomingStory(from = others[it], sentTimeMillis = System.currentTimeMillis()).get().messageId
    }

    val viewedIds: List<Long> = (0 until 5).map {
      Thread.sleep(5)
      insertIncomingStory(from = others[it], sentTimeMillis = System.currentTimeMillis()).get().messageId
    }

    val interspersedIds: List<Long> = (0 until 10).map {
      Thread.sleep(5)
      if (it % 2 == 0) {
        mms.setIncomingMessageViewed(viewedIds[it / 2])
        viewedIds[it / 2]
      } else {
        insertOutgoingStory(recipient = myStory, sentTimeMillis = System.currentTimeMillis(), threadId = myStoryThread)
      }
    }

    val result = mms.getOrderedStoryRecipientsAndIds(false)
    val resultOrderedIds = result.map { it.messageId }

    assertEquals(unviewedIds.reversed() + interspersedIds.reversed(), resultOrderedIds)
  }

  @Test
  fun givenNoStories_whenICheckIsOutgoingStoryAlreadyInDatabase_thenIExpectFalse() {
    // WHEN
    val result = mms.isOutgoingStoryAlreadyInDatabase(others[0], 200)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenNoOutgoingStories_whenICheckIsOutgoingStoryAlreadyInDatabase_thenIExpectFalse() {
    // GIVEN
    insertIncomingStory(from = others[0], sentTimeMillis = 200)

    // WHEN
    val result = mms.isOutgoingStoryAlreadyInDatabase(others[0], 200)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenOutgoingStoryExistsForRecipientAndTime_whenICheckIsOutgoingStoryAlreadyInDatabase_thenIExpectTrue() {
    // GIVEN
    insertOutgoingStory(recipient = myStory, sentTimeMillis = 200)

    // WHEN
    val result = mms.isOutgoingStoryAlreadyInDatabase(myStory.id, 200)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenAGroupStoryWithNoReplies_whenICheckHasSelfReplyInGroupStory_thenIExpectFalse() {
    // GIVEN
    val groupStoryId = insertOutgoingStory(recipient = myStory, sentTimeMillis = 200)

    // WHEN
    val result = mms.hasGroupReplyOrReactionInStory(groupStoryId)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenAGroupStoryWithAReplyFromSelf_whenICheckHasSelfReplyInGroupStory_thenIExpectTrue() {
    // GIVEN
    val groupStoryId = insertOutgoingStory(recipient = myStory, sentTimeMillis = 200)

    insertOutgoingStoryReply(recipient = myStory, sentTimeMillis = 201, parentStoryId = ParentStoryId.GroupReply(groupStoryId))

    // WHEN
    val result = mms.hasGroupReplyOrReactionInStory(groupStoryId)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenAGroupStoryWithAReactionFromSelf_whenICheckHasSelfReplyInGroupStory_thenIExpectTrue() {
    // GIVEN
    val groupStoryId = insertOutgoingStory(recipient = myStory, sentTimeMillis = 200)

    insertOutgoingStoryReply(
      recipient = myStory,
      sentTimeMillis = 201,
      parentStoryId = ParentStoryId.GroupReply(groupStoryId),
      isStoryReaction = true
    )

    // WHEN
    val result = mms.hasGroupReplyOrReactionInStory(groupStoryId)

    // THEN
    assertTrue(result)
  }

  @Test
  fun givenAGroupStoryWithAReplyFromSomeoneElse_whenICheckHasSelfReplyInGroupStory_thenIExpectFalse() {
    // GIVEN
    val groupStoryId = insertOutgoingStory(recipient = myStory, sentTimeMillis = 200)

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(myStory, ThreadTable.DistributionTypes.DEFAULT)
    mms.insertMessageInbox(
      IncomingMessage(
        type = MessageType.NORMAL,
        from = myStory.id,
        sentTimeMillis = 201,
        serverTimeMillis = 201,
        receivedTimeMillis = 202,
        parentStoryId = ParentStoryId.GroupReply(groupStoryId)
      ),
      threadId
    )

    // WHEN
    val result = mms.hasGroupReplyOrReactionInStory(groupStoryId)

    // THEN
    assertFalse(result)
  }

  @Test
  fun givenNotViewedOnboardingAndOnlyStoryIsOnboardingAndAdded2DaysAgo_whenIGetOldestStoryTimestamp_thenIExpectNull() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(releaseChannelRecipient)
    insertOutgoingStory(
      recipient = releaseChannelRecipient,
      sentTimeMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2),
      threadId = threadId
    )

    // WHEN
    val oldestTimestamp = mms.getOldestStorySendTimestamp(false)

    // THEN
    assertNull(oldestTimestamp)
  }

  @Test
  fun givenViewedOnboardingAndOnlyStoryIsOnboardingAndAdded2DaysAgo_whenIGetOldestStoryTimestamp_thenIExpectNotNull() {
    // GIVEN
    val expected = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(releaseChannelRecipient)
    insertOutgoingStory(recipient = releaseChannelRecipient, sentTimeMillis = expected, threadId = threadId)

    // WHEN
    val oldestTimestamp = mms.getOldestStorySendTimestamp(true)

    // THEN
    assertEquals(expected, oldestTimestamp)
  }

  // region deleteUnarchivedStoriesOlderThan

  @Test
  fun givenExpiredStory_whenIDeleteUnarchivedStoriesOlderThan_thenStoryIsDeleted() {
    // GIVEN
    val sender = others[0]
    val storyId = insertIncomingStory(from = sender, sentTimeMillis = 100L).get().messageId

    // WHEN
    val deleted = mms.deleteUnarchivedStoriesOlderThan(150L, hasSeenReleaseChannelStories = true)

    // THEN
    assertEquals(1, deleted)
    assertNull(mms.getMessageRecordOrNull(storyId))
  }

  @Test
  fun givenFreshStory_whenIDeleteUnarchivedStoriesOlderThan_thenStoryIsPreserved() {
    // GIVEN
    val sender = others[0]
    val storyId = insertIncomingStory(from = sender, sentTimeMillis = 200L).get().messageId

    // WHEN
    val deleted = mms.deleteUnarchivedStoriesOlderThan(150L, hasSeenReleaseChannelStories = true)

    // THEN
    assertEquals(0, deleted)
    assertFalse(mms.getMessageRecordOrNull(storyId) == null)
  }

  @Test
  fun givenExpiredStoryWithGroupReply_whenIDeleteUnarchivedStoriesOlderThan_thenGroupReplyIsDeleted() {
    // GIVEN
    val sender = others[0]
    val storyId = insertIncomingStory(from = sender, sentTimeMillis = 100L).get().messageId
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(sender), ThreadTable.DistributionTypes.DEFAULT)
    val replyId = insertIncomingGroupReply(from = sender, sentTimeMillis = 101L, parentStoryId = storyId, threadId = threadId)

    // WHEN
    mms.deleteUnarchivedStoriesOlderThan(150L, hasSeenReleaseChannelStories = true)

    // THEN
    assertNull(mms.getMessageRecordOrNull(replyId))
  }

  @Test
  fun givenExpiredStoryWithDirectReply_whenIDeleteUnarchivedStoriesOlderThan_thenDirectReplyIsDisassociated() {
    // GIVEN
    val sender = others[0]
    val storyId = insertIncomingStory(from = sender, sentTimeMillis = 100L).get().messageId
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(sender), ThreadTable.DistributionTypes.DEFAULT)
    val replyId = insertIncomingDirectReply(from = sender, sentTimeMillis = 101L, parentStoryId = storyId, threadId = threadId)

    // WHEN
    mms.deleteUnarchivedStoriesOlderThan(150L, hasSeenReleaseChannelStories = true)

    // THEN
    assertTrue(getQuote(replyId)!!.isOriginalMissing)
    assertEquals("", getQuote(replyId)!!.displayText.toString())
  }

  @Test
  fun givenFreshStoryWithReplies_whenIDeleteUnarchivedStoriesOlderThan_thenRepliesArePreserved() {
    // GIVEN
    val sender = others[0]
    val storyId = insertIncomingStory(from = sender, sentTimeMillis = 200L).get().messageId
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(sender), ThreadTable.DistributionTypes.DEFAULT)
    val groupReplyId = insertIncomingGroupReply(from = sender, sentTimeMillis = 201L, parentStoryId = storyId, threadId = threadId)
    val directReplyId = insertIncomingDirectReply(from = sender, sentTimeMillis = 202L, parentStoryId = storyId, threadId = threadId)

    // WHEN
    mms.deleteUnarchivedStoriesOlderThan(150L, hasSeenReleaseChannelStories = true)

    // THEN
    assertFalse(mms.getMessageRecordOrNull(groupReplyId) == null)
    assertFalse(getQuote(directReplyId)!!.isOriginalMissing)
  }

  // endregion

  // region deleteStoriesForRecipient

  @Test
  fun givenStoriesForRecipient_whenIDeleteStoriesForRecipient_thenStoriesAreDeleted() {
    // GIVEN
    val sender = others[0]
    val story1 = insertIncomingStory(from = sender, sentTimeMillis = 100L).get().messageId
    val story2 = insertIncomingStory(from = sender, sentTimeMillis = 200L).get().messageId

    // WHEN
    val deleted = mms.deleteStoriesForRecipient(sender)

    // THEN
    assertEquals(2, deleted)
    assertNull(mms.getMessageRecordOrNull(story1))
    assertNull(mms.getMessageRecordOrNull(story2))
  }

  @Test
  fun givenStoriesForMultipleRecipients_whenIDeleteStoriesForRecipient_thenOtherStoriesArePreserved() {
    // GIVEN
    val sender = others[0]
    val otherSender = others[1]
    insertIncomingStory(from = sender, sentTimeMillis = 100L)
    val otherStory = insertIncomingStory(from = otherSender, sentTimeMillis = 100L).get().messageId

    // WHEN
    mms.deleteStoriesForRecipient(sender)

    // THEN
    assertFalse(mms.getMessageRecordOrNull(otherStory) == null)
  }

  @Test
  fun givenStoryWithGroupReply_whenIDeleteStoriesForRecipient_thenGroupReplyIsDeleted() {
    // GIVEN
    val sender = others[0]
    val storyId = insertIncomingStory(from = sender, sentTimeMillis = 100L).get().messageId
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(sender), ThreadTable.DistributionTypes.DEFAULT)
    val replyId = insertIncomingGroupReply(from = sender, sentTimeMillis = 101L, parentStoryId = storyId, threadId = threadId)

    // WHEN
    mms.deleteStoriesForRecipient(sender)

    // THEN
    assertNull(mms.getMessageRecordOrNull(replyId))
  }

  @Test
  fun givenStoryWithDirectReply_whenIDeleteStoriesForRecipient_thenDirectReplyIsDisassociated() {
    // GIVEN
    val sender = others[0]
    val storyId = insertIncomingStory(from = sender, sentTimeMillis = 100L).get().messageId
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(sender), ThreadTable.DistributionTypes.DEFAULT)
    val replyId = insertIncomingDirectReply(from = sender, sentTimeMillis = 101L, parentStoryId = storyId, threadId = threadId)

    // WHEN
    mms.deleteStoriesForRecipient(sender)

    // THEN
    assertTrue(getQuote(replyId)!!.isOriginalMissing)
    assertEquals("", getQuote(replyId)!!.displayText.toString())
  }

  @Test
  fun givenNoThread_whenIDeleteStoriesForRecipient_thenReturnZero() {
    // GIVEN
    val recipient = recipients.createRecipient("No Thread")

    // WHEN
    val deleted = mms.deleteStoriesForRecipient(recipient)

    // THEN
    assertEquals(0, deleted)
  }

  // endregion

  private fun insertOutgoingStory(
    recipient: Recipient,
    sentTimeMillis: Long,
    threadId: Long = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
  ): Long {
    val message = OutgoingMessage(
      recipient = recipient,
      body = "body",
      timestamp = sentTimeMillis,
      storyType = StoryType.STORY_WITH_REPLIES,
      isSecure = true
    )
    return recipients.insertOutgoingMessage(message, threadId)
  }

  private fun insertOutgoingStoryReply(
    recipient: Recipient,
    sentTimeMillis: Long,
    parentStoryId: ParentStoryId,
    isStoryReaction: Boolean = false,
    threadId: Long = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
  ): Long {
    val message = OutgoingMessage(
      recipient = recipient,
      body = "body",
      timestamp = sentTimeMillis,
      storyType = StoryType.NONE,
      parentStoryId = parentStoryId,
      isStoryReaction = isStoryReaction,
      isSecure = true
    )
    return recipients.insertOutgoingMessage(message, threadId)
  }

  private fun insertIncomingStory(from: RecipientId, sentTimeMillis: Long): Optional<MessageTable.InsertResult> {
    return mms.insertMessageInbox(
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

  private fun insertIncomingGroupReply(from: RecipientId, sentTimeMillis: Long, parentStoryId: Long, threadId: Long): Long {
    return mms.insertMessageInbox(
      IncomingMessage(
        type = MessageType.NORMAL,
        from = from,
        sentTimeMillis = sentTimeMillis,
        serverTimeMillis = sentTimeMillis,
        receivedTimeMillis = sentTimeMillis,
        parentStoryId = ParentStoryId.GroupReply(parentStoryId)
      ),
      threadId
    ).get().messageId
  }

  private fun insertIncomingDirectReply(from: RecipientId, sentTimeMillis: Long, parentStoryId: Long, threadId: Long): Long {
    return mms.insertMessageInbox(
      IncomingMessage(
        type = MessageType.NORMAL,
        from = from,
        sentTimeMillis = sentTimeMillis,
        serverTimeMillis = sentTimeMillis,
        receivedTimeMillis = sentTimeMillis,
        parentStoryId = ParentStoryId.DirectReply(parentStoryId),
        quote = QuoteModel(parentStoryId, from, "original story text", false, null, null, QuoteModel.Type.NORMAL, null)
      ),
      threadId
    ).get().messageId
  }

  private fun getQuote(messageId: Long) = (mms.getMessageRecord(messageId) as MmsMessageRecord).quote
}
