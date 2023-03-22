package org.thoughtcrime.securesms.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.util.delete
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalActivityRule
import java.security.SecureRandom
import kotlin.random.Random

class GroupTableTest {

  @get:Rule
  val harness = SignalActivityRule()

  private lateinit var groupTable: GroupTable

  @Before
  fun setUp() {
    groupTable = SignalDatabase.groups

    groupTable.writableDatabase.delete(GroupTable.TABLE_NAME).run()
    groupTable.writableDatabase.delete(GroupTable.MembershipTable.TABLE_NAME).run()
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
  fun givenAGroupV2_whenIGetGroupsContainingMember_thenIExpectGroup() {
    val groupId = insertPushGroup()
    insertThread(groupId)

    val groups = groupTable.getGroupsContainingMember(harness.others[0], false)

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
  fun givenGroups_whenIQueryGroupsByMembership_thenIExpectBothGroups() {
    insertPushGroup()
    insertMmsGroup(members = listOf(harness.others[1]))

    val groups = groupTable.queryGroupsByMembership(
      setOf(harness.self.id, harness.others[1]),
      includeInactive = false,
      excludeV1 = false,
      excludeMms = false
    )

    assertEquals(2, groups.cursor?.count)
  }

  @Test
  fun givenGroups_whenIGetGroups_thenIExpectBothGroups() {
    insertPushGroup()
    insertMmsGroup(members = listOf(harness.others[1]))

    val groups = groupTable.getGroups()

    assertEquals(2, groups.cursor?.count)
  }

  @Test
  fun givenAGroup_whenIGetGroup_thenIExpectGroup() {
    val v2Group = insertPushGroup()
    insertThread(v2Group)

    val groupRecord = groupTable.getGroup(v2Group).get()
    assertEquals(setOf(harness.self.id, harness.others[0]), groupRecord.members.toSet())
  }

  @Test
  fun givenAGroupAndARemap_whenIGetGroup_thenIExpectRemap() {
    val v2Group = insertPushGroup()
    insertThread(v2Group)

    groupTable.writableDatabase.withinTransaction {
      RemappedRecords.getInstance().addRecipient(harness.others[0], harness.others[1])
    }

    val groupRecord = groupTable.getGroup(v2Group).get()
    assertEquals(setOf(harness.self.id, harness.others[1]), groupRecord.members.toSet())
  }

  @Test
  fun givenAGroupAndMember_whenIIsCurrentMember_thenIExpectTrue() {
    val v2Group = insertPushGroup()

    val actual = groupTable.isCurrentMember(v2Group.requirePush(), harness.others[0])

    assertTrue(actual)
  }

  @Test
  fun givenAGroupAndMember_whenIRemove_thenIExpectNotAMember() {
    val v2Group = insertPushGroup()

    groupTable.remove(v2Group, harness.others[0])
    val actual = groupTable.isCurrentMember(v2Group.requirePush(), harness.others[0])

    assertFalse(actual)
  }

  @Test
  fun givenAGroupAndNonMember_whenIIsCurrentMember_thenIExpectFalse() {
    val v2Group = insertPushGroup()

    val actual = groupTable.isCurrentMember(v2Group.requirePush(), harness.others[1])

    assertFalse(actual)
  }

  @Test
  fun givenAGroup_whenIUpdateMembers_thenIExpectUpdatedMembers() {
    val v2Group = insertPushGroup()
    groupTable.updateMembers(v2Group, listOf(harness.self.id, harness.others[1]))
    val groupRecord = groupTable.getGroup(v2Group)

    assertEquals(setOf(harness.self.id, harness.others[1]), groupRecord.get().members.toSet())
  }

  @Test
  fun givenAnMmsGroup_whenIGetOrCreateMmsGroup_thenIExpectMyMmsGroup() {
    val members: List<RecipientId> = listOf(harness.self.id, harness.others[0])
    val other = insertMmsGroup(members + listOf(harness.others[1]))
    val mmsGroup = insertMmsGroup(members)
    val actual = groupTable.getOrCreateMmsGroupForMembers(members.toSet())

    assertNotEquals(other, actual)
    assertEquals(mmsGroup, actual)
  }

  @Test
  fun givenMultipleMmsGroups_whenIGetOrCreateMmsGroup_thenIExpectMyMmsGroup() {
    val group1Members: List<RecipientId> = listOf(harness.self.id, harness.others[0], harness.others[1])
    val group2Members: List<RecipientId> = listOf(harness.self.id, harness.others[0], harness.others[2])

    val group1: GroupId = insertMmsGroup(group1Members)
    val group2: GroupId = insertMmsGroup(group2Members)

    val group1Result: GroupId = groupTable.getOrCreateMmsGroupForMembers(group1Members.toSet())
    val group2Result: GroupId = groupTable.getOrCreateMmsGroupForMembers(group2Members.toSet())

    assertEquals(group1, group1Result)
    assertEquals(group2, group2Result)
    assertNotEquals(group1Result, group2Result)
  }

  @Test
  fun givenMultipleMmsGroupsWithDifferentMemberOrders_whenIGetOrCreateMmsGroup_thenIExpectMyMmsGroup() {
    val group1Members: List<RecipientId> = listOf(harness.self.id, harness.others[0], harness.others[1], harness.others[2]).shuffled()
    val group2Members: List<RecipientId> = listOf(harness.self.id, harness.others[0], harness.others[2], harness.others[3]).shuffled()

    val group1: GroupId = insertMmsGroup(group1Members)
    val group2: GroupId = insertMmsGroup(group2Members)

    val group1Result: GroupId = groupTable.getOrCreateMmsGroupForMembers(group1Members.shuffled().toSet())
    val group2Result: GroupId = groupTable.getOrCreateMmsGroupForMembers(group2Members.shuffled().toSet())

    assertEquals(group1, group1Result)
    assertEquals(group2, group2Result)
    assertNotEquals(group1Result, group2Result)
  }

  @Test
  fun givenMmsGroupWithOneMember_whenIGetOrCreateMmsGroup_thenIExpectMyMmsGroup() {
    val groupMembers: List<RecipientId> = listOf(harness.self.id)
    val group: GroupId = insertMmsGroup(groupMembers)

    val groupResult: GroupId = groupTable.getOrCreateMmsGroupForMembers(groupMembers.toSet())

    assertEquals(group, groupResult)
  }

  @Test
  fun givenTwoGroupsWithoutMembers_whenIQueryThem_thenIExpectEach() {
    val g1 = insertPushGroup(listOf())
    val g2 = insertPushGroup(listOf())

    val gr1 = groupTable.getGroup(g1)
    val gr2 = groupTable.getGroup(g2)

    assertEquals(g1, gr1.get().id)
    assertEquals(g2, gr2.get().id)
  }

  @Test
  fun givenASharedActiveGroupWithoutAThread_whenISearchForRecipientsWithGroupsInCommon_thenIExpectThatGroup() {
    val groupInCommon = insertPushGroup()
    val expected = Recipient.resolved(harness.others[0])

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

  private fun insertThread(groupId: GroupId): Long {
    val groupRecipient = SignalDatabase.recipients.getByGroupId(groupId).get()
    return SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(groupRecipient))
  }

  private fun insertMmsGroup(members: List<RecipientId> = listOf(harness.self.id, harness.others[0])): GroupId {
    val id = GroupId.createMms(SecureRandom())
    groupTable.create(
      id,
      null,
      members.apply {
        println("Creating a group with ${members.size} members")
      }
    )

    return id
  }

  private fun insertPushGroup(
    members: List<DecryptedMember> = listOf(
      DecryptedMember.newBuilder()
        .setUuid(harness.self.requireServiceId().toByteString())
        .setJoinedAtRevision(0)
        .setRole(Member.Role.DEFAULT)
        .build(),
      DecryptedMember.newBuilder()
        .setUuid(Recipient.resolved(harness.others[0]).requireServiceId().toByteString())
        .setJoinedAtRevision(0)
        .setRole(Member.Role.DEFAULT)
        .build()
    )
  ): GroupId {
    val groupMasterKey = GroupMasterKey(Random.nextBytes(GroupMasterKey.SIZE))
    val decryptedGroupState = DecryptedGroup.newBuilder()
      .addAllMembers(members)
      .setRevision(0)
      .build()

    return groupTable.create(groupMasterKey, decryptedGroupState)
  }
}
