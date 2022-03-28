package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorySendsDatabaseTest {

  private lateinit var recipients1to10: List<RecipientId>
  private lateinit var recipients11to20: List<RecipientId>
  private lateinit var recipients6to15: List<RecipientId>
  private lateinit var recipients6to10: List<RecipientId>

  private var messageId1: Long = 0
  private var messageId2: Long = 0
  private var messageId3: Long = 0

  private lateinit var storySends: StorySendsDatabase

  @Before
  fun setup() {
    storySends = SignalDatabase.storySends

    messageId1 = MmsHelper.insert(storyType = StoryType.STORY_WITHOUT_REPLIES)
    messageId2 = MmsHelper.insert(storyType = StoryType.STORY_WITH_REPLIES)
    messageId3 = MmsHelper.insert(storyType = StoryType.STORY_WITHOUT_REPLIES)

    recipients1to10 = makeRecipients(10)
    recipients11to20 = makeRecipients(10)

    recipients6to15 = recipients1to10.takeLast(5) + recipients11to20.take(5)
    recipients6to10 = recipients1to10.takeLast(5)
  }

  @Test
  fun getRecipientsToSendTo_noOverlap() {
    storySends.insert(messageId1, recipients1to10, 100, false)
    storySends.insert(messageId2, recipients11to20, 200, true)
    storySends.insert(messageId3, recipients1to10, 300, false)

    val recipientIdsForMessage1 = storySends.getRecipientsToSendTo(messageId1, 100, false)
    val recipientIdsForMessage2 = storySends.getRecipientsToSendTo(messageId2, 200, true)

    assertThat(recipientIdsForMessage1, hasSize(10))
    assertThat(recipientIdsForMessage1, containsInAnyOrder(*recipients1to10.toTypedArray()))

    assertThat(recipientIdsForMessage2, hasSize(10))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients11to20.toTypedArray()))
  }

  @Test
  fun getRecipientsToSendTo_overlap() {
    storySends.insert(messageId1, recipients1to10, 100, false)
    storySends.insert(messageId2, recipients6to15, 100, true)

    val recipientIdsForMessage1 = storySends.getRecipientsToSendTo(messageId1, 100, false)
    val recipientIdsForMessage2 = storySends.getRecipientsToSendTo(messageId2, 100, true)

    assertThat(recipientIdsForMessage1, hasSize(5))
    assertThat(recipientIdsForMessage1, containsInAnyOrder(*recipients1to10.take(5).toTypedArray()))

    assertThat(recipientIdsForMessage2, hasSize(10))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients6to15.toTypedArray()))
  }

  @Test
  fun getRecipientsToSendTo_overlapAll() {
    val recipient1 = recipients1to10.first()
    val recipient2 = recipients11to20.first()

    storySends.insert(messageId1, listOf(recipient1, recipient2), 100, false)
    storySends.insert(messageId2, listOf(recipient1), 100, true)
    storySends.insert(messageId3, listOf(recipient2), 100, true)

    val recipientIdsForMessage1 = storySends.getRecipientsToSendTo(messageId1, 100, false)
    val recipientIdsForMessage2 = storySends.getRecipientsToSendTo(messageId2, 100, true)
    val recipientIdsForMessage3 = storySends.getRecipientsToSendTo(messageId3, 100, true)

    assertThat(recipientIdsForMessage1, hasSize(0))

    assertThat(recipientIdsForMessage2, hasSize(1))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(recipient1))

    assertThat(recipientIdsForMessage3, hasSize(1))
    assertThat(recipientIdsForMessage3, containsInAnyOrder(recipient2))
  }

  @Test
  fun getRecipientsToSendTo_overlapWithEarlierMessage() {
    storySends.insert(messageId1, recipients6to15, 100, true)
    storySends.insert(messageId2, recipients1to10, 100, false)

    val recipientIdsForMessage1 = storySends.getRecipientsToSendTo(messageId1, 100, true)
    val recipientIdsForMessage2 = storySends.getRecipientsToSendTo(messageId2, 100, false)

    assertThat(recipientIdsForMessage1, hasSize(10))
    assertThat(recipientIdsForMessage1, containsInAnyOrder(*recipients6to15.toTypedArray()))

    assertThat(recipientIdsForMessage2, hasSize(5))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients1to10.take(5).toTypedArray()))
  }

  @Test
  fun getRemoteDeleteRecipients_noOverlap() {
    storySends.insert(messageId1, recipients1to10, 100, false)
    storySends.insert(messageId2, recipients11to20, 200, true)
    storySends.insert(messageId3, recipients1to10, 300, false)

    val recipientIdsForMessage1 = storySends.getRemoteDeleteRecipients(messageId1, 100)
    val recipientIdsForMessage2 = storySends.getRemoteDeleteRecipients(messageId2, 200)

    assertThat(recipientIdsForMessage1, hasSize(10))
    assertThat(recipientIdsForMessage1, containsInAnyOrder(*recipients1to10.toTypedArray()))

    assertThat(recipientIdsForMessage2, hasSize(10))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients11to20.toTypedArray()))
  }

  @Test
  fun getRemoteDeleteRecipients_overlapNoPreviousDeletes() {
    storySends.insert(messageId1, recipients1to10, 200, false)
    storySends.insert(messageId2, recipients6to15, 200, true)

    val recipientIdsForMessage1 = storySends.getRemoteDeleteRecipients(messageId1, 200)
    val recipientIdsForMessage2 = storySends.getRemoteDeleteRecipients(messageId2, 200)

    assertThat(recipientIdsForMessage1, hasSize(5))
    assertThat(recipientIdsForMessage1, containsInAnyOrder(*recipients1to10.take(5).toTypedArray()))

    assertThat(recipientIdsForMessage2, hasSize(5))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients6to15.takeLast(5).toTypedArray()))
  }

  @Test
  fun getRemoteDeleteRecipients_overlapWithPreviousDeletes() {
    storySends.insert(messageId1, recipients1to10, 200, false)
    SignalDatabase.mms.markAsRemoteDelete(messageId1)

    storySends.insert(messageId2, recipients6to15, 200, true)

    val recipientIdsForMessage2 = storySends.getRemoteDeleteRecipients(messageId2, 200)

    assertThat(recipientIdsForMessage2, hasSize(10))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients6to15.toTypedArray()))
  }

  @Test
  fun canReply_storyWithReplies() {
    storySends.insert(messageId2, recipients1to10, 200, true)

    val canReply = storySends.canReply(recipients1to10[0], 200)

    assertThat(canReply, `is`(true))
  }

  @Test
  fun canReply_storyWithoutReplies() {
    storySends.insert(messageId1, recipients1to10, 200, false)

    val canReply = storySends.canReply(recipients1to10[0], 200)

    assertThat(canReply, `is`(false))
  }

  @Test
  fun canReply_storyWithAndWithoutRepliesOverlap() {
    storySends.insert(messageId1, recipients1to10, 200, false)
    storySends.insert(messageId2, recipients6to10, 200, true)

    val message1OnlyRecipientCanReply = storySends.canReply(recipients1to10[0], 200)
    val message2RecipientCanReply = storySends.canReply(recipients6to10[0], 200)

    assertThat(message1OnlyRecipientCanReply, `is`(false))
    assertThat(message2RecipientCanReply, `is`(true))
  }

  private fun makeRecipients(count: Int): List<RecipientId> {
    return (1..count).map {
      SignalDatabase.recipients.getOrInsertFromServiceId(ServiceId.from(UUID.randomUUID()))
    }
  }
}
