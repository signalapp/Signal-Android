/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import io.mockk.every
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.SqlUtil
import org.signal.core.util.deleteAll
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.storage.protos.groups.Member
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import java.security.SecureRandom
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupTableTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var groupTable: GroupTable
  private lateinit var threadTable: ThreadTable
  private lateinit var alice: RecipientId
  private lateinit var bob: RecipientId

  @Before
  fun setUp() {
    groupTable = SignalDatabase.groups
    threadTable = SignalDatabase.threads

    groupTable.writableDatabase.deleteAll(GroupTable.TABLE_NAME)
    groupTable.writableDatabase.deleteAll(GroupTable.MembershipTable.TABLE_NAME)

    threadTable.writableDatabase.deleteAll(ThreadTable.TABLE_NAME)

    alice = recipients.createRecipient("Buddy #0")
    bob = recipients.createRecipient("Buddy #1")
  }

  @Test
  fun whenICreateGroupV2_thenIExpectMemberRowsPopulated() {
    val groupId = insertPushGroup()

    //language=sql
    val members: List<RecipientId> = groupTable.writableDatabase.query(
      """
      SELECT ${GroupTable.MembershipTable.RECIPIENT_ID}
      FROM ${GroupTable.MembershipTable.TABLE_NAME}
      WHERE ${GroupTable.MembershipTable.GROUP_ID} = "${groupId.serialize()}"
      """.trimIndent()
    ).readToList {
      RecipientId.from(it.requireLong(GroupTable.RECIPIENT_ID))
    }

    assertEquals(2, members.size)
  }

  @Test
  fun givenALeftGroup_whenIDeleteGroup_thenIExpectGroupDeleted() {
    val groupId = insertPushGroup()
    val threadId = insertThread(groupId)

    groupTable.setMember(groupId, false)

    threadTable.deleteConversation(threadId)
    groupTable.clearGroupIfLeftAndDeleted(groupId)

    assertFalse(groupTable.getGroup(groupId).isPresent)
  }

  @Test
  fun givenATerminatedGroup_whenIDeleteGroup_thenIExpectGroupDeleted() {
    val groupId = insertPushGroup()
    val threadId = insertThread(groupId)

    groupTable.setMember(groupId, true)
    groupTable.setTerminatedBy(groupId, alice)
    threadTable.deleteConversation(threadId)

    groupTable.clearGroupIfLeftAndDeleted(groupId)

    assertFalse(groupTable.getGroup(groupId).isPresent)
  }

  @Test
  fun givenALeftGroup_whenIDeleteGroup_thenIExpectMembershipDeleted() {
    val groupId = insertPushGroup()
    val threadId = insertThread(groupId)

    groupTable.setMember(groupId, false)
    threadTable.deleteConversation(threadId)

    groupTable.clearGroupIfLeftAndDeleted(groupId)

    val remainingMembers = groupTable.getGroupMembers(groupId, GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF)

    assertTrue(remainingMembers.isEmpty())
  }

  @Test
  fun givenAGroupWeAreStillAMemberOf_whenIDeleteGroup_thenIExpectGroupRetained() {
    val groupId = insertPushGroup()
    val threadId = insertThread(groupId)

    threadTable.deleteConversation(threadId)
    groupTable.clearGroupIfLeftAndDeleted(groupId)

    assertTrue(groupTable.getGroup(groupId).isPresent)
  }

  @Test
  fun givenAGroup_whenILeave_thenIExpectGroupRetained() {
    val groupId = insertPushGroup()
    val threadId = insertThread(groupId)
    SignalDatabase.threads.markAsActiveEarly(threadId)
    groupTable.setMember(groupId, false)

    groupTable.clearGroupIfLeftAndDeleted(groupId)

    assertTrue(groupTable.getGroup(groupId).isPresent)
  }

  @Test
  fun givenALeftGroup_whenIBlockAndDelete_thenIExpectGroupIdRetained() {
    val groupId = insertPushGroup()
    val threadId = insertThread(groupId)
    val recipientId = SignalDatabase.recipients.getByGroupId(groupId).get()

    groupTable.setMember(groupId, false)
    SignalDatabase.recipients.setBlocked(recipientId, true, 0)
    SignalDatabase.threads.deleteConversation(threadId)

    groupTable.clearGroupIfLeftAndDeleted(groupId)

    assertEquals(groupId, groupTable.getGroup(groupId).get().id)
    assertFalse(groupTable.getGroup(groupId).get().hasV2GroupProperties)
  }

  @Test
  fun givenALeftGroupOnAMultiDeviceAccount_whenIDelete_thenIExpectGroupStubRetainedWithoutProperties() {
    val groupId = insertPushGroup()
    val threadId = insertThread(groupId)

    every { recipients.signalStore.account.isMultiDevice } returns true
    groupTable.setMember(groupId, false)
    threadTable.deleteConversation(threadId)

    groupTable.clearGroupIfLeftAndDeleted(groupId)

    val record = groupTable.getGroup(groupId)
    assertTrue(record.isPresent)
    assertFalse(record.get().hasV2GroupProperties)
    assertTrue(SignalDatabase.recipients.getByGroupId(groupId).isPresent)
  }

  @Test
  fun givenALeftGroupOnASingleDevice_whenIDelete_thenIExpectRecipientRowAlsoDeleted() {
    val groupId = insertPushGroup()
    val threadId = insertThread(groupId)

    groupTable.setMember(groupId, false)
    threadTable.deleteConversation(threadId)

    groupTable.clearGroupIfLeftAndDeleted(groupId)

    assertFalse(groupTable.getGroup(groupId).isPresent)
    assertFalse(SignalDatabase.recipients.getByGroupId(groupId).isPresent)
  }

  @Test
  fun givenAMemberWithADeletedThread_whenILeaveLater_thenIExpectClearOnlyAfterTheSecondEvent() {
    val groupId = insertPushGroup()
    val threadId = insertThread(groupId)

    threadTable.deleteConversation(threadId)
    groupTable.clearGroupIfLeftAndDeleted(groupId)
    assertTrue("Still a member, so the deleted thread alone must not clear the group", groupTable.getGroup(groupId).isPresent)

    groupTable.setMember(groupId, false)
    groupTable.clearGroupIfLeftAndDeleted(groupId)
    assertFalse("Leaving after the thread was deleted should trigger the clear", groupTable.getGroup(groupId).isPresent)
  }

  @Test
  fun givenALeftGroup_whenIClearByRecipientId_thenIExpectGroupDeleted() {
    val groupId = insertPushGroup()
    val threadId = insertThread(groupId)
    val recipientId = SignalDatabase.recipients.getByGroupId(groupId).get()

    groupTable.setMember(groupId, false)
    threadTable.deleteConversation(threadId)

    groupTable.clearGroupIfLeftAndDeleted(recipientId)

    assertFalse(groupTable.getGroup(groupId).isPresent)
  }

  @Test
  fun givenAGroupV2_whenIGetGroupsContainingMember_thenIExpectGroup() {
    val groupId = insertPushGroup()
    insertThread(groupId)

    val groups = groupTable.getGroupsContainingMember(alice, false)

    assertEquals(1, groups.size)
    assertEquals(groupId, groups[0].id)
  }

  @Test
  fun givenAnMmsGroup_whenIGetMembers_thenIExpectAllMembers() {
    val groupId = insertMmsGroup()

    val groups = groupTable.getGroupMemberIds(groupId, GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF)

    assertEquals(2, groups.size)
  }

  @Test
  fun givenGroups_whenIGetGroups_thenIExpectBothGroups() {
    insertPushGroup()
    insertMmsGroup(members = listOf(bob))

    val groups = groupTable.getGroups()

    assertEquals(2, groups.cursor?.count)
  }

  @Test
  fun givenAGroup_whenIGetGroup_thenIExpectGroup() {
    val v2Group = insertPushGroup()
    insertThread(v2Group)

    val groupRecord = groupTable.getGroup(v2Group).get()
    assertEquals(setOf(recipients.self, alice), groupRecord.members.toSet())
  }

  @Test
  fun givenAGroupAndARemap_whenIGetGroup_thenIExpectRemap() {
    val v2Group = insertPushGroup()
    insertThread(v2Group)

    groupTable.writableDatabase.withinTransaction {
      RemappedRecords.getInstance().addRecipient(alice, bob)
    }

    val groupRecord = groupTable.getGroup(v2Group).get()
    assertEquals(setOf(recipients.self, bob), groupRecord.members.toSet())
  }

  @Test
  fun givenAGroup_whenIRemapRecipientsThatHaveAConflict_thenIExpectDeletion() {
    val v2Group = insertPushGroupWithSelfAndOthers(listOf(alice, bob))

    insertThread(v2Group)

    groupTable.remapRecipient(alice, bob)

    val groupRecord = groupTable.getGroup(v2Group).get()

    assertEquals(setOf(recipients.self, bob), groupRecord.members.toSet())
  }

  @Test
  fun givenAGroup_whenIRemapRecipients_thenIExpectRemap() {
    val v2Group = insertPushGroup()
    insertThread(v2Group)

    groupTable.remapRecipient(alice, bob)

    val groupRecord = groupTable.getGroup(v2Group).get()

    assertEquals(setOf(recipients.self, bob), groupRecord.members.toSet())
  }

  @Test
  fun givenAGroupAndMember_whenIIsCurrentMember_thenIExpectTrue() {
    val v2Group = insertPushGroup()

    val actual = groupTable.isCurrentMember(v2Group.requirePush(), alice)

    assertTrue(actual)
  }

  @Test
  fun givenAGroupAndMember_whenIRemove_thenIExpectNotAMember() {
    val v2Group = insertPushGroup()

    groupTable.remove(v2Group, alice)
    val actual = groupTable.isCurrentMember(v2Group.requirePush(), alice)

    assertFalse(actual)
  }

  @Test
  fun givenAGroupAndNonMember_whenIIsCurrentMember_thenIExpectFalse() {
    val v2Group = insertPushGroup()

    val actual = groupTable.isCurrentMember(v2Group.requirePush(), bob)

    assertFalse(actual)
  }

  @Test
  fun givenTwoGroupsWithoutMembers_whenIQueryThem_thenIExpectEach() {
    val g1 = insertPushGroup(members = emptyList())
    val g2 = insertPushGroup(members = emptyList())

    val gr1 = groupTable.getGroup(g1)
    val gr2 = groupTable.getGroup(g2)

    assertEquals(g1, gr1.get().id)
    assertEquals(g2, gr2.get().id)
  }

  @Test
  fun givenASharedActiveGroupWithoutAThread_whenISearchForRecipientsWithGroupsInCommon_thenIExpectThatGroup() {
    val groupInCommon = insertPushGroup()
    val expected = Recipient.resolved(alice)

    SignalDatabase.recipients.setProfileSharing(expected.id, false)

    SignalDatabase.recipients.queryGroupMemberContacts("Buddy")!!.use {
      assertTrue(it.moveToFirst())
      assertEquals(1, it.count)
      assertEquals(expected.id.toLong(), it.requireLong(RecipientTable.ID))
    }

    val groups = groupTable.getPushGroupsContainingMember(expected.id)
    assertEquals(1, groups.size)
    assertEquals(groups[0].id, groupInCommon)
  }

  @Test
  fun givenTwoGroupsWithANameThatSharesAToken_whenISearchForTheSharedToken_thenIExpectBothGroups() {
    insertPushGroup("Group Alice")
    insertPushGroup("Group Bob")

    SignalDatabase.groups.queryGroupsByTitle(
      inputQuery = "Group",
      includeInactive = false,
      excludeV1 = false,
      excludeMms = false
    ).use {
      assertEquals(2, it.cursor?.count)

      val firstGroup = it.getNext()
      val secondGroup = it.getNext()

      assertEquals("Group Alice", firstGroup?.title)
      assertEquals("Group Bob", secondGroup?.title)
    }
  }

  @Test
  fun givenTwoGroupsWithANameThatSharesAToken_whenISearchForAnUnsharedToken_thenIExpectOneGroup() {
    insertPushGroup("Group Alice")
    insertPushGroup("Group Bob")

    SignalDatabase.groups.queryGroupsByTitle(
      inputQuery = "Alice",
      includeInactive = false,
      excludeV1 = false,
      excludeMms = false
    ).use {
      assertEquals(1, it.cursor?.count)

      val firstGroup = it.getNext()

      assertEquals("Group Alice", firstGroup?.title)
    }
  }

  @Test
  fun givenAGroupWithThreeTokens_whenISearchForTheFirstAndLastToken_thenIExpectThatGroup() {
    insertPushGroup("Group & Alice")

    SignalDatabase.groups.queryGroupsByTitle(
      inputQuery = "Group Alice",
      includeInactive = false,
      excludeV1 = false,
      excludeMms = false
    ).use {
      assertEquals(1, it.cursor?.count)

      val firstGroup = it.getNext()

      assertEquals("Group & Alice", firstGroup?.title)
    }
  }

  @Test
  fun givenTwoGroupsWithSharedTokens_whenISearchForAnExactMatch_thenIExpectThatGroupFirst() {
    insertPushGroup("Group Alice Bob")
    insertPushGroup("Group Bob")

    SignalDatabase.groups.queryGroupsByTitle(
      inputQuery = "Group Bob",
      includeInactive = false,
      excludeV1 = false,
      excludeMms = false
    ).use {
      assertEquals(2, it.cursor?.count)

      val firstGroup = it.getNext()
      val second = it.getNext()

      assertEquals("Group Bob", firstGroup?.title)
      assertEquals("Group Alice Bob", second?.title)
    }
  }

  private fun insertThread(groupId: GroupId): Long {
    val groupRecipient = SignalDatabase.recipients.getByGroupId(groupId).get()
    return SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(groupRecipient))
  }

  private fun insertMmsGroup(members: List<RecipientId> = listOf(recipients.self, alice)): GroupId {
    val id = GroupId.createMms(SecureRandom())
    groupTable.create(id, null, members)
    return id
  }

  private fun insertPushGroup(
    title: String = "Test Group",
    members: List<DecryptedMember> = listOf(
      DecryptedMember.Builder()
        .aciBytes(recipients.selfAci.toByteString())
        .joinedAtRevision(0)
        .role(Member.Role.DEFAULT)
        .build(),
      DecryptedMember.Builder()
        .aciBytes(Recipient.resolved(alice).requireAci().toByteString())
        .joinedAtRevision(0)
        .role(Member.Role.DEFAULT)
        .build()
    )
  ): GroupId {
    val groupMasterKey = GroupMasterKey(Random.nextBytes(GroupMasterKey.SIZE))
    val decryptedGroupState = DecryptedGroup.Builder()
      .title(title)
      .members(members)
      .revision(0)
      .build()

    return groupTable.create(groupMasterKey, decryptedGroupState, null)!!
  }

  private fun insertPushGroupWithSelfAndOthers(others: List<RecipientId>): GroupId {
    val groupMasterKey = GroupMasterKey(Random.nextBytes(GroupMasterKey.SIZE))

    val selfMember: DecryptedMember = DecryptedMember.Builder()
      .aciBytes(recipients.selfAci.toByteString())
      .joinedAtRevision(0)
      .role(Member.Role.DEFAULT)
      .build()

    val otherMembers: List<DecryptedMember> = others.map { id ->
      DecryptedMember.Builder()
        .aciBytes(Recipient.resolved(id).requireAci().toByteString())
        .joinedAtRevision(0)
        .role(Member.Role.DEFAULT)
        .build()
    }

    val decryptedGroupState = DecryptedGroup.Builder()
      .members(listOf(selfMember) + otherMembers)
      .revision(0)
      .build()

    return groupTable.create(groupMasterKey, decryptedGroupState, null)!!
  }

  /**
   * Guards [GroupTable.clearGroupRecipient]: every group column must be either blanked by [GroupTable.buildClearedGroupValues]
   * or explicitly listed here as intentionally preserved. Adding a column without categorizing it fails this test so we don't silently leak it.
   */
  @Test
  fun buildClearedGroupValues_accountsForEveryColumn() {
    val keptColumns = setOf(
      GroupTable.ID,
      GroupTable.RECIPIENT_ID,
      GroupTable.GROUP_ID,
      GroupTable.V2_MASTER_KEY
    )

    val clearedColumns = groupTable.buildClearedGroupValues().keySet()
    val allColumns = SqlUtil.getAllColumns(groupTable.writableDatabase, GroupTable.TABLE_NAME)
    val uncategorized = allColumns - clearedColumns - keptColumns

    assertThat(allColumns).isNotEmpty()
    assertThat(uncategorized).isEmpty()
  }
}
