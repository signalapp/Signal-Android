package org.thoughtcrime.securesms.database

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ACI
import java.util.UUID

class DistributionListTablesTest {

  private lateinit var distributionDatabase: DistributionListTables

  @Before
  fun setup() {
    distributionDatabase = SignalDatabase.distributionLists
  }

  @Test
  fun createList_whenNoConflict_insertSuccessfully() {
    val id: DistributionListId? = distributionDatabase.createList("test", recipientList(1, 2, 3))
    Assert.assertNotNull(id)
  }

  @Test
  fun createList_whenNameConflict_failToInsert() {
    val id: DistributionListId? = distributionDatabase.createList("test", recipientList(1, 2, 3))
    Assert.assertNotNull(id)

    val id2: DistributionListId? = distributionDatabase.createList("test", recipientList(1, 2, 3))
    Assert.assertNull(id2)
  }

  @Test
  fun getList_returnCorrectList() {
    createRecipients(3)
    val members: List<RecipientId> = recipientList(1, 2, 3)

    val id: DistributionListId? = distributionDatabase.createList("test", members)
    Assert.assertNotNull(id)

    val record: DistributionListRecord? = distributionDatabase.getList(id!!)
    Assert.assertNotNull(record)
    Assert.assertEquals(id, record!!.id)
    Assert.assertEquals("test", record.name)
    Assert.assertEquals(members, record.members)
  }

  @Test
  fun getMembers_returnsCorrectMembers() {
    createRecipients(3)
    val members: List<RecipientId> = recipientList(1, 2, 3)

    val id: DistributionListId? = distributionDatabase.createList("test", members)
    Assert.assertNotNull(id)

    val foundMembers: List<RecipientId> = distributionDatabase.getMembers(id!!)
    Assert.assertEquals(members, foundMembers)
  }

  @Test
  fun givenStoryExists_getStoryType_returnsStoryWithReplies() {
    val id: DistributionListId? = distributionDatabase.createList("test", recipientList(1, 2, 3))
    Assert.assertNotNull(id)

    val storyType = distributionDatabase.getStoryType(id!!)
    Assert.assertEquals(StoryType.STORY_WITH_REPLIES, storyType)
  }

  @Test
  fun givenStoryExistsAndMarkedNoReplies_getStoryType_returnsStoryWithoutReplies() {
    val id: DistributionListId? = distributionDatabase.createList("test", recipientList(1, 2, 3))
    Assert.assertNotNull(id)
    distributionDatabase.setAllowsReplies(id!!, false)

    val storyType = distributionDatabase.getStoryType(id)
    Assert.assertEquals(StoryType.STORY_WITHOUT_REPLIES, storyType)
  }

  @Test(expected = IllegalStateException::class)
  fun givenStoryDoesNotExist_getStoryType_throwsIllegalStateException() {
    distributionDatabase.getStoryType(DistributionListId.from(12))
    Assert.fail("Expected an assertion error.")
  }

  private fun createRecipients(count: Int) {
    for (i in 0 until count) {
      SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))
    }
  }

  private fun recipientList(vararg ids: Long): List<RecipientId> {
    return ids.map { RecipientId.from(it) }
  }
}
