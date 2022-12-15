package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertNull
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorySendTableTest {

  private val distributionId1 = DistributionId.from(UUID.randomUUID())
  private val distributionId2 = DistributionId.from(UUID.randomUUID())
  private val distributionId3 = DistributionId.from(UUID.randomUUID())

  private lateinit var distributionList1: DistributionListId
  private lateinit var distributionList2: DistributionListId
  private lateinit var distributionList3: DistributionListId

  private lateinit var distributionListRecipient1: Recipient
  private lateinit var distributionListRecipient2: Recipient
  private lateinit var distributionListRecipient3: Recipient

  private lateinit var recipients1to10: List<RecipientId>
  private lateinit var recipients11to20: List<RecipientId>
  private lateinit var recipients6to15: List<RecipientId>
  private lateinit var recipients6to10: List<RecipientId>

  private var messageId1: Long = 0
  private var messageId2: Long = 0
  private var messageId3: Long = 0

  private lateinit var storySends: StorySendTable

  @Before
  fun setup() {
    storySends = SignalDatabase.storySends

    recipients1to10 = makeRecipients(10)
    recipients11to20 = makeRecipients(10)

    distributionList1 = SignalDatabase.distributionLists.createList("1", emptyList(), distributionId = distributionId1)!!
    distributionList2 = SignalDatabase.distributionLists.createList("2", emptyList(), distributionId = distributionId2)!!
    distributionList3 = SignalDatabase.distributionLists.createList("3", emptyList(), distributionId = distributionId3)!!

    distributionListRecipient1 = Recipient.resolved(SignalDatabase.recipients.getOrInsertFromDistributionListId(distributionList1))
    distributionListRecipient2 = Recipient.resolved(SignalDatabase.recipients.getOrInsertFromDistributionListId(distributionList2))
    distributionListRecipient3 = Recipient.resolved(SignalDatabase.recipients.getOrInsertFromDistributionListId(distributionList3))

    messageId1 = MmsHelper.insert(
      recipient = distributionListRecipient1,
      storyType = StoryType.STORY_WITHOUT_REPLIES,
    )

    messageId2 = MmsHelper.insert(
      recipient = distributionListRecipient2,
      storyType = StoryType.STORY_WITH_REPLIES,
    )

    messageId3 = MmsHelper.insert(
      recipient = distributionListRecipient3,
      storyType = StoryType.STORY_WITHOUT_REPLIES,
    )

    recipients6to15 = recipients1to10.takeLast(5) + recipients11to20.take(5)
    recipients6to10 = recipients1to10.takeLast(5)
  }

  @Test
  fun getRecipientsToSendTo_noOverlap() {
    storySends.insert(messageId1, recipients1to10, 100, false, distributionId1)
    storySends.insert(messageId2, recipients11to20, 200, true, distributionId2)
    storySends.insert(messageId3, recipients1to10, 300, false, distributionId3)

    val recipientIdsForMessage1 = storySends.getRecipientsToSendTo(messageId1, 100, false)
    val recipientIdsForMessage2 = storySends.getRecipientsToSendTo(messageId2, 200, true)

    assertThat(recipientIdsForMessage1, hasSize(10))
    assertThat(recipientIdsForMessage1, containsInAnyOrder(*recipients1to10.toTypedArray()))

    assertThat(recipientIdsForMessage2, hasSize(10))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients11to20.toTypedArray()))
  }

  @Test
  fun getRecipientsToSendTo_overlap() {
    storySends.insert(messageId1, recipients1to10, 100, false, distributionId1)
    storySends.insert(messageId2, recipients6to15, 100, true, distributionId2)

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

    storySends.insert(messageId1, listOf(recipient1, recipient2), 100, false, distributionId1)
    storySends.insert(messageId2, listOf(recipient1), 100, true, distributionId2)
    storySends.insert(messageId3, listOf(recipient2), 100, true, distributionId3)

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
    storySends.insert(messageId1, recipients6to15, 100, true, distributionId1)
    storySends.insert(messageId2, recipients1to10, 100, false, distributionId2)

    val recipientIdsForMessage1 = storySends.getRecipientsToSendTo(messageId1, 100, true)
    val recipientIdsForMessage2 = storySends.getRecipientsToSendTo(messageId2, 100, false)

    assertThat(recipientIdsForMessage1, hasSize(10))
    assertThat(recipientIdsForMessage1, containsInAnyOrder(*recipients6to15.toTypedArray()))

    assertThat(recipientIdsForMessage2, hasSize(5))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients1to10.take(5).toTypedArray()))
  }

  @Test
  fun getRemoteDeleteRecipients_noOverlap() {
    storySends.insert(messageId1, recipients1to10, 100, false, distributionId1)
    storySends.insert(messageId2, recipients11to20, 200, true, distributionId2)
    storySends.insert(messageId3, recipients1to10, 300, false, distributionId3)

    val recipientIdsForMessage1 = storySends.getRemoteDeleteRecipients(messageId1, 100)
    val recipientIdsForMessage2 = storySends.getRemoteDeleteRecipients(messageId2, 200)

    assertThat(recipientIdsForMessage1, hasSize(10))
    assertThat(recipientIdsForMessage1, containsInAnyOrder(*recipients1to10.toTypedArray()))

    assertThat(recipientIdsForMessage2, hasSize(10))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients11to20.toTypedArray()))
  }

  @Test
  fun getRemoteDeleteRecipients_overlapNoPreviousDeletes() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    storySends.insert(messageId2, recipients6to15, 200, true, distributionId2)

    val recipientIdsForMessage1 = storySends.getRemoteDeleteRecipients(messageId1, 200)
    val recipientIdsForMessage2 = storySends.getRemoteDeleteRecipients(messageId2, 200)

    assertThat(recipientIdsForMessage1, hasSize(5))
    assertThat(recipientIdsForMessage1, containsInAnyOrder(*recipients1to10.take(5).toTypedArray()))

    assertThat(recipientIdsForMessage2, hasSize(5))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients6to15.takeLast(5).toTypedArray()))
  }

  @Test
  fun getRemoteDeleteRecipients_overlapWithPreviousDeletes() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    SignalDatabase.messages.markAsRemoteDelete(messageId1)

    storySends.insert(messageId2, recipients6to15, 200, true, distributionId2)

    val recipientIdsForMessage2 = storySends.getRemoteDeleteRecipients(messageId2, 200)

    assertThat(recipientIdsForMessage2, hasSize(10))
    assertThat(recipientIdsForMessage2, containsInAnyOrder(*recipients6to15.toTypedArray()))
  }

  @Test
  fun canReply_storyWithReplies() {
    storySends.insert(messageId2, recipients1to10, 200, true, distributionId2)

    val canReply = storySends.canReply(recipients1to10[0], 200)

    assertThat(canReply, `is`(true))
  }

  @Test
  fun canReply_storyWithoutReplies() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)

    val canReply = storySends.canReply(recipients1to10[0], 200)

    assertThat(canReply, `is`(false))
  }

  @Test
  fun canReply_storyWithAndWithoutRepliesOverlap() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    storySends.insert(messageId2, recipients6to10, 200, true, distributionId2)

    val message1OnlyRecipientCanReply = storySends.canReply(recipients1to10[0], 200)
    val message2RecipientCanReply = storySends.canReply(recipients6to10[0], 200)

    assertThat(message1OnlyRecipientCanReply, `is`(false))
    assertThat(message2RecipientCanReply, `is`(true))
  }

  @Test
  fun givenASingleStory_whenIGetFullSentStorySyncManifest_thenIExpectNotNull() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)

    val manifest = storySends.getFullSentStorySyncManifest(messageId1, 200)

    assertNotNull(manifest)
  }

  @Test
  fun givenTwoStories_whenIGetFullSentStorySyncManifestForStory2_thenIExpectNull() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    storySends.insert(messageId2, recipients1to10, 200, false, distributionId2)

    val manifest = storySends.getFullSentStorySyncManifest(messageId2, 200)

    assertNull(manifest)
  }

  @Test
  fun givenTwoStories_whenIGetFullSentStorySyncManifestForStory1_thenIExpectOneManifestPerRecipient() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    storySends.insert(messageId2, recipients1to10, 200, true, distributionId2)

    val manifest = storySends.getFullSentStorySyncManifest(messageId1, 200)!!

    assertEquals(recipients1to10, manifest.entries.map { it.recipientId })
  }

  @Test
  fun givenTwoStories_whenIGetFullSentStorySyncManifestForStory1_thenIExpectTwoListsPerRecipient() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    storySends.insert(messageId2, recipients1to10, 200, true, distributionId2)

    val manifest = storySends.getFullSentStorySyncManifest(messageId1, 200)!!

    manifest.entries.forEach { entry ->
      assertEquals(listOf(distributionId1, distributionId2), entry.distributionLists)
    }
  }

  @Test
  fun givenTwoStories_whenIGetFullSentStorySyncManifestForStory1_thenIExpectAllRecipientsCanReply() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    storySends.insert(messageId2, recipients1to10, 200, true, distributionId2)

    val manifest = storySends.getFullSentStorySyncManifest(messageId1, 200)!!

    manifest.entries.forEach { entry ->
      assertTrue(entry.allowedToReply)
    }
  }

  @Test
  fun givenTwoStoriesAndOneIsRemoteDeleted_whenIGetFullSentStorySyncManifestForStory2_thenIExpectNonNullResult() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    storySends.insert(messageId2, recipients1to10, 200, true, distributionId2)
    SignalDatabase.messages.markAsRemoteDelete(messageId1)

    val manifest = storySends.getFullSentStorySyncManifest(messageId2, 200)!!

    assertNotNull(manifest)
  }

  /*
  @Test
  fun givenTwoStoriesAndOneIsRemoteDeleted_whenIGetRecipientIdsForManifestUpdate_thenIExpectOnlyRecipientsWithStory2() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    storySends.insert(messageId1, recipients11to20, 200, false, distributionId1)
    storySends.insert(messageId2, recipients1to10, 200, true, distributionId2)
    SignalDatabase.messages.markAsRemoteDelete(messageId1)

    val recipientIds = storySends.getRecipientIdsForManifestUpdate(200, messageId1)

    assertEquals(recipients1to10.toHashSet(), recipientIds)
  }

  @Test
  fun givenTwoStoriesAndOneIsRemoteDeleted_whenIGetPartialSentStorySyncManifest_thenIExpectOnlyRecipientsThatHadStory1() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    storySends.insert(messageId2, recipients1to10, 200, true, distributionId2)
    storySends.insert(messageId2, recipients11to20, 200, true, distributionId2)
    SignalDatabase.messages.markAsRemoteDelete(messageId1)
    val recipientIds = storySends.getRecipientIdsForManifestUpdate(200, messageId1)

    val results = storySends.getSentStorySyncManifestForUpdate(200, recipientIds)

    val manifestRecipients = results.entries.map { it.recipientId }
    assertEquals(recipients1to10, manifestRecipients)
  }

  @Test
  fun givenTwoStoriesAndTheOneThatAllowedRepliesIsRemoteDeleted_whenIGetPartialSentStorySyncManifest_thenIExpectAllowRepliesToBeTrue() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    storySends.insert(messageId2, recipients1to10, 200, true, distributionId2)
    SignalDatabase.messages.markAsRemoteDelete(messageId2)
    val recipientIds = storySends.getRecipientIdsForManifestUpdate(200, messageId1)

    val results = storySends.getSentStorySyncManifestForUpdate(200, recipientIds)

    assertTrue(results.entries.all { it.allowedToReply })
  }
   */
  @Test
  fun givenEmptyManifest_whenIApplyRemoteManifest_thenNothingChanges() {
    storySends.insert(messageId1, recipients1to10, 200, false, distributionId1)
    val expected = storySends.getFullSentStorySyncManifest(messageId1, 200)
    val emptyManifest = SentStorySyncManifest(emptyList())

    storySends.applySentStoryManifest(emptyManifest, 200)
    val result = storySends.getFullSentStorySyncManifest(messageId1, 200)

    assertEquals(expected, result)
  }

  @Test
  fun givenAnIdenticalManifest_whenIApplyRemoteManifest_thenNothingChanges() {
    val messageId4 = MmsHelper.insert(
      recipient = distributionListRecipient1,
      storyType = StoryType.STORY_WITHOUT_REPLIES,
      sentTimeMillis = 200
    )

    storySends.insert(messageId4, recipients1to10, 200, false, distributionId1)
    val expected = storySends.getFullSentStorySyncManifest(messageId4, 200)

    storySends.applySentStoryManifest(expected!!, 200)
    val result = storySends.getFullSentStorySyncManifest(messageId4, 200)

    assertEquals(expected, result)
  }

  @Test(expected = NoSuchMessageException::class)
  fun givenAManifest_whenIApplyRemoteManifestWithoutOneList_thenIExpectMessageToBeDeleted() {
    val messageId4 = MmsHelper.insert(
      recipient = distributionListRecipient1,
      storyType = StoryType.STORY_WITHOUT_REPLIES,
      sentTimeMillis = 200
    )

    val messageId5 = MmsHelper.insert(
      recipient = distributionListRecipient2,
      storyType = StoryType.STORY_WITHOUT_REPLIES,
      sentTimeMillis = 200
    )

    storySends.insert(messageId4, recipients1to10, 200, false, distributionId1)
    val remote = storySends.getFullSentStorySyncManifest(messageId4, 200)!!

    storySends.insert(messageId5, recipients1to10, 200, false, distributionId2)

    storySends.applySentStoryManifest(remote, 200)

    SignalDatabase.messages.getMessageRecord(messageId5)
    fail("Expected messageId5 to no longer exist.")
  }

  @Test
  fun givenAManifest_whenIApplyRemoteManifestWithoutOneList_thenIExpectSharedMessageToNotBeMarkedRemoteDeleted() {
    val messageId4 = MmsHelper.insert(
      recipient = distributionListRecipient1,
      storyType = StoryType.STORY_WITHOUT_REPLIES,
      sentTimeMillis = 200
    )

    val messageId5 = MmsHelper.insert(
      recipient = distributionListRecipient2,
      storyType = StoryType.STORY_WITHOUT_REPLIES,
      sentTimeMillis = 200
    )

    storySends.insert(messageId4, recipients1to10, 200, false, distributionId1)
    val remote = storySends.getFullSentStorySyncManifest(messageId4, 200)!!

    storySends.insert(messageId5, recipients1to10, 200, false, distributionId2)

    storySends.applySentStoryManifest(remote, 200)

    assertFalse(SignalDatabase.messages.getMessageRecord(messageId4).isRemoteDelete)
  }

  @Test
  fun givenNoLocalEntries_whenIApplyRemoteManifest_thenIExpectLocalManifestToMatch() {
    val messageId4 = MmsHelper.insert(
      recipient = distributionListRecipient1,
      storyType = StoryType.STORY_WITHOUT_REPLIES,
      sentTimeMillis = 2000
    )

    val remote = SentStorySyncManifest(
      recipients1to10.map {
        SentStorySyncManifest.Entry(
          recipientId = it,
          allowedToReply = true,
          distributionLists = listOf(distributionId1)
        )
      }
    )

    storySends.applySentStoryManifest(remote, 2000)

    val local = storySends.getFullSentStorySyncManifest(messageId4, 2000)
    assertEquals(remote, local)
  }

  @Test
  fun givenNonStoryMessageAtSentTimestamp_whenIApplyRemoteManifest_thenIExpectLocalManifestToMatchAndNoCrashes() {
    val messageId4 = MmsHelper.insert(
      recipient = distributionListRecipient1,
      storyType = StoryType.STORY_WITHOUT_REPLIES,
      sentTimeMillis = 2000
    )

    MmsHelper.insert(
      recipient = Recipient.resolved(recipients1to10.first()),
      sentTimeMillis = 2000
    )

    val remote = SentStorySyncManifest(
      recipients1to10.map {
        SentStorySyncManifest.Entry(
          recipientId = it,
          allowedToReply = true,
          distributionLists = listOf(distributionId1)
        )
      }
    )

    storySends.applySentStoryManifest(remote, 2000)

    val local = storySends.getFullSentStorySyncManifest(messageId4, 2000)
    assertEquals(remote, local)
  }

  private fun makeRecipients(count: Int): List<RecipientId> {
    return (1..count).map {
      SignalDatabase.recipients.getOrInsertFromServiceId(ServiceId.from(UUID.randomUUID()))
    }
  }
}
