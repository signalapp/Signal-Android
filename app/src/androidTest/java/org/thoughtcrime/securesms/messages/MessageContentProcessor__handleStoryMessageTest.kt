package org.thoughtcrime.securesms.messages

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.signal.core.util.requireLong
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MmsHelper
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.TestProtos
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto
import kotlin.random.Random

@Suppress("ClassName")
class MessageContentProcessor__handleStoryMessageTest : MessageContentProcessorTest() {

  @Before
  fun setUp() {
    SignalDatabase.messages.deleteAllThreads()
  }

  @After
  fun tearDown() {
    SignalDatabase.messages.deleteAllThreads()
  }

  @Test
  fun givenContentWithADirectStoryReplyWhenIProcessThenIInsertAReplyInTheCorrectThread() {
    val sender = Recipient.resolved(harness.others.first())
    val senderThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(sender)
    val myStory = Recipient.resolved(SignalDatabase.distributionLists.getRecipientId(DistributionListId.MY_STORY)!!)
    val myStoryThread = SignalDatabase.threads.getOrCreateThreadIdFor(myStory)
    val expectedSentTime = 200L
    val storyMessageId = MmsHelper.insert(
      sentTimeMillis = expectedSentTime,
      recipient = myStory,
      storyType = StoryType.STORY_WITH_REPLIES,
      threadId = myStoryThread
    )

    SignalDatabase.storySends.insert(
      messageId = storyMessageId,
      recipientIds = listOf(sender.id),
      sentTimestamp = expectedSentTime,
      allowsReplies = true,
      distributionId = DistributionId.MY_STORY
    )

    val expectedBody = "Hello!"

    val storyContent: SignalServiceContentProto = createServiceContentWithStoryContext(
      messageSender = sender,
      storyAuthor = harness.self,
      storySentTimestamp = expectedSentTime
    ) {
      body = expectedBody
    }

    runTestWithContent(contentProto = storyContent)

    val replyId = SignalDatabase.messages.getConversation(senderThreadId, 0, 1).use {
      it.moveToFirst()
      it.requireLong(MessageTable.ID)
    }

    val replyRecord = SignalDatabase.messages.getMessageRecord(replyId) as MediaMmsMessageRecord
    assertEquals(ParentStoryId.DirectReply(storyMessageId).serialize(), replyRecord.parentStoryId!!.serialize())
    assertEquals(expectedBody, replyRecord.body)

    SignalDatabase.messages.deleteAllThreads()
  }

  @Test
  fun givenContentWithAGroupStoryReplyWhenIProcessThenIInsertAReplyToTheCorrectStory() {
    val sender = Recipient.resolved(harness.others[0])
    val groupMasterKey = GroupMasterKey(Random.nextBytes(GroupMasterKey.SIZE))
    val decryptedGroupState = DecryptedGroup.newBuilder()
      .addAllMembers(
        listOf(
          DecryptedMember.newBuilder()
            .setUuid(harness.self.requireServiceId().toByteString())
            .setJoinedAtRevision(0)
            .setRole(Member.Role.DEFAULT)
            .build(),
          DecryptedMember.newBuilder()
            .setUuid(sender.requireServiceId().toByteString())
            .setJoinedAtRevision(0)
            .setRole(Member.Role.DEFAULT)
            .build()
        )
      )
      .setRevision(0)
      .build()

    val group = SignalDatabase.groups.create(
      groupMasterKey,
      decryptedGroupState
    )

    val groupRecipient = Recipient.externalGroupExact(group)
    val threadForGroup = SignalDatabase.threads.getOrCreateThreadIdFor(groupRecipient)

    val insertResult = MmsHelper.insert(
      message = IncomingMediaMessage(
        from = sender.id,
        sentTimeMillis = 100L,
        serverTimeMillis = 101L,
        receivedTimeMillis = 102L,
        storyType = StoryType.STORY_WITH_REPLIES
      ),
      threadId = threadForGroup
    )

    val expectedBody = "Hello, World!"
    val storyContent: SignalServiceContentProto = createServiceContentWithStoryContext(
      messageSender = sender,
      storyAuthor = sender,
      storySentTimestamp = 100L
    ) {
      groupV2 = TestProtos.build { groupContextV2(masterKeyBytes = groupMasterKey.serialize()).build() }
      body = expectedBody
    }

    runTestWithContent(storyContent)

    val replyId = SignalDatabase.messages.getStoryReplies(insertResult.get().messageId).use { cursor ->
      assertEquals(1, cursor.count)
      cursor.moveToFirst()
      cursor.requireLong(MessageTable.ID)
    }

    val replyRecord = SignalDatabase.messages.getMessageRecord(replyId) as MediaMmsMessageRecord
    assertEquals(ParentStoryId.GroupReply(insertResult.get().messageId).serialize(), replyRecord.parentStoryId?.serialize())
    assertEquals(threadForGroup, replyRecord.threadId)
    assertEquals(expectedBody, replyRecord.body)

    SignalDatabase.messages.deleteGroupStoryReplies(insertResult.get().messageId)
    SignalDatabase.messages.deleteAllThreads()
  }

  /**
   * Creates a ServiceContent proto with a StoryContext, and then
   * uses `injectDataMessage` to fill in the data message object.
   */
  private fun createServiceContentWithStoryContext(
    messageSender: Recipient,
    storyAuthor: Recipient,
    storySentTimestamp: Long,
    injectDataMessage: DataMessage.Builder.() -> Unit
  ): SignalServiceContentProto {
    return createServiceContentWithDataMessage(messageSender) {
      storyContext = TestProtos.build {
        storyContext(
          sentTimestamp = storySentTimestamp,
          authorUuid = storyAuthor.requireServiceId().toString()
        ).build()
      }
      injectDataMessage()
    }
  }

  private fun runTestWithContent(contentProto: SignalServiceContentProto) {
    val content = SignalServiceContent.createFromProto(contentProto)
    val testSubject = createNormalContentTestSubject()
    testSubject.doProcess(content = content!!)
  }
}
