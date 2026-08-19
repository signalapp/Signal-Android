package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.update
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.storage.StorageId
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DistributionListTablesTest {

  @get:Rule
  val recipients = RecipientTestRule()

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
  fun getList_returnCorrectList() {
    val members: List<RecipientId> = createRecipients(3)

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
    val members: List<RecipientId> = createRecipients(3)

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

  @Test
  fun `given lists deleted long ago and recently, when I age off storage ids, then I expect only the old one to lose its storage id`() {
    val old = insertList("old", deletedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(46))
    val recent = insertList("recent", deletedAt = System.currentTimeMillis())

    distributionDatabase.removeStorageIdsFromOldDeletedLists(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45))

    assertThat(storageIdOf(old)).isNull()
    assertThat(storageIdOf(recent)).isNotNull()
  }

  @Test
  fun `given an old deleted list and a live list, when I age off storage ids, then I expect only the deleted one to lose its storage id`() {
    val deleted = insertList("deleted", deletedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(46))
    val live = insertList("live")

    distributionDatabase.removeStorageIdsFromOldDeletedLists(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45))

    assertThat(storageIdOf(deleted)).isNull()
    assertThat(storageIdOf(live)).isNotNull()
  }

  @Test
  fun `given My Story is somehow tombstoned, when I age off storage ids, then I expect it to keep its storage id`() {
    val deleted = insertList("deleted", deletedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(46))
    val myStory = distributionDatabase.getRecipientId(DistributionListId.MY_STORY)!!
    setDeletionTimestamp(DistributionListId.MY_STORY, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(46))

    distributionDatabase.removeStorageIdsFromOldDeletedLists(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45))

    assertThat(storageIdOf(deleted)).isNull()
    assertThat(storageIdOf(myStory)).isNotNull()
  }

  @Test
  fun `given a deleted list and a live list whose storage ids are local only, then I expect only the deleted one to lose its storage id`() {
    val deleted = insertList("deleted", deletedAt = System.currentTimeMillis())
    val live = insertList("live")

    distributionDatabase.removeStorageIdsFromLocalOnlyDeletedLists(listOf(storageIdOf(deleted)!!, storageIdOf(live)!!))

    assertThat(storageIdOf(deleted)).isNull()
    assertThat(storageIdOf(live)).isNotNull()
  }

  @Test
  fun `given a list deleted one minute ago, when its storage id is local only, then I expect it to lose its storage id`() {
    val deleted = insertList("deleted", deletedAt = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1))

    distributionDatabase.removeStorageIdsFromLocalOnlyDeletedLists(listOf(storageIdOf(deleted)!!))

    assertThat(storageIdOf(deleted)).isNull()
  }

  @Test
  fun `given two deleted lists, when only one storage id is local only, then I expect only that one to lose its storage id`() {
    val localOnly = insertList("localOnly", deletedAt = System.currentTimeMillis())
    val stillRemote = insertList("stillRemote", deletedAt = System.currentTimeMillis())

    distributionDatabase.removeStorageIdsFromLocalOnlyDeletedLists(listOf(storageIdOf(localOnly)!!))

    assertThat(storageIdOf(localOnly)).isNull()
    assertThat(storageIdOf(stillRemote)).isNotNull()
  }

  @Test
  fun `given My Story is somehow tombstoned, when its storage id is local only, then I expect it to keep its storage id`() {
    val deleted = insertList("deleted", deletedAt = System.currentTimeMillis())
    val myStory = distributionDatabase.getRecipientId(DistributionListId.MY_STORY)!!
    setDeletionTimestamp(DistributionListId.MY_STORY, System.currentTimeMillis())

    distributionDatabase.removeStorageIdsFromLocalOnlyDeletedLists(listOf(storageIdOf(deleted)!!, storageIdOf(myStory)!!))

    assertThat(storageIdOf(deleted)).isNull()
    assertThat(storageIdOf(myStory)).isNotNull()
  }

  private fun createRecipients(count: Int): List<RecipientId> {
    return (0 until count).map {
      SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))
    }
  }

  private fun recipientList(vararg ids: Long): List<RecipientId> {
    return ids.map { RecipientId.from(it) }
  }

  private fun insertList(name: String, deletedAt: Long? = null): RecipientId {
    val listId = distributionDatabase.createList(name, emptyList())!!

    if (deletedAt != null) {
      distributionDatabase.deleteList(listId, deletedAt)
    }

    return distributionDatabase.getRecipientId(listId)!!
  }

  private fun storageIdOf(recipientId: RecipientId): StorageId? {
    return SignalDatabase.recipients.getContactStorageSyncIdsMap()[recipientId]
  }

  private fun setDeletionTimestamp(listId: DistributionListId, timestamp: Long) {
    distributionDatabase.writableDatabase
      .update(DistributionListTables.ListTable.TABLE_NAME)
      .values(DistributionListTables.ListTable.DELETION_TIMESTAMP to timestamp)
      .where("${DistributionListTables.ListTable.ID} = ?", listId.serialize())
      .run()
  }
}
