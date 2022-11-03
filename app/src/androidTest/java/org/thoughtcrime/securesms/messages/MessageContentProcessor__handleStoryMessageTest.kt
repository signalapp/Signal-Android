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
import org.thoughtcrime.securesms.database.MessageDatabase
import org.thoughtcrime.securesms.database.MmsHelper
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.TestProtos
import org.thoughtcrime.securesms.util.FeatureFlagsTestUtil
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto
import kotlin.random.Random

@Suppress("ClassName")
class MessageContentProcessor__handleStoryMessageTest : MessageContentProcessorTest() {

  @Before
  fun setUp() {
    FeatureFlagsTestUtil.setStoriesEnabled(true)
    SignalDatabase.mms.deleteAllThreads()
  }

  @After
  fun tearDown() {
    SignalDatabase.mms.deleteAllThreads()
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
    val storyContent: SignalServiceContentProto = TestProtos.build {
      serviceContent(
        localAddress = address(uuid = harness.self.requireServiceId().uuid()).build(),
        metadata = metadata(
          address = address(uuid = sender.requireServiceId().uuid()).build()
        ).build()
      ).apply {
        content = content().apply {
          dataMessage = dataMessage().apply {
            storyContext = storyContext(
              sentTimestamp = 100L,
              authorUuid = sender.requireServiceId().toString()
            ).build()

            groupV2 = groupContextV2(masterKeyBytes = groupMasterKey.serialize()).build()
            body = expectedBody
          }.build()
        }.build()
      }.build()
    }

    runTestWithContent(storyContent)

    val replyId = SignalDatabase.mms.getStoryReplies(insertResult.get().messageId).use { cursor ->
      assertEquals(1, cursor.count)
      cursor.moveToFirst()
      cursor.requireLong(MessageDatabase.ID)
    }

    val replyRecord = SignalDatabase.mms.getMessageRecord(replyId) as MediaMmsMessageRecord
    assertEquals(ParentStoryId.GroupReply(insertResult.get().messageId).serialize(), replyRecord.parentStoryId?.serialize())
    assertEquals(threadForGroup, replyRecord.threadId)
    assertEquals(expectedBody, replyRecord.body)

    SignalDatabase.mms.deleteGroupStoryReplies(insertResult.get().messageId)
  }

  private fun runTestWithContent(contentProto: SignalServiceContentProto) {
    val content = SignalServiceContent.createFromProto(contentProto)
    val testSubject = createNormalContentTestSubject()
    testSubject.doProcess(content = content)
  }
}
