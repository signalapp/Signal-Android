package org.thoughtcrime.securesms.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.util.deleteAll
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

    groupTable.writableDatabase.deleteAll(GroupTable.TABLE_NAME)
    groupTable.writableDatabase.deleteAll(GroupTable.MembershipTable.TABLE_NAME)
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
  fun givenAGroup_whenIRemapRecipientsThatHaveAConflict_thenIExpectDeletion() {
    val v2Group = insertPushGroupWithSelfAndOthers(
      listOf(
        harness.others[0],
        harness.others[1]
      )
    )

    insertThread(v2Group)

    groupTable.remapRecipient(harness.others[0], harness.others[1])

    val groupRecord = groupTable.getGroup(v2Group).get()

    assertEquals(setOf(harness.self.id, harness.others[1]), groupRecord.members.toSet())
  }

  @Test
  fun givenAGroup_whenIRemapRecipients_thenIExpectRemap() {
    val v2Group = insertPushGroup()
    insertThread(v2Group)

    val newId = harness.others[1]
    groupTable.remapRecipient(harness.others[0], newId)

    val groupRecord = groupTable.getGroup(v2Group).get()

    assertEquals(setOf(harness.self.id, newId), groupRecord.members.toSet())
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
    title: String = "Test Group",
    members: List<DecryptedMember> = listOf(
      DecryptedMember.Builder()
        .aciBytes(harness.self.requireAci().toByteString())
        .joinedAtRevision(0)
        .role(Member.Role.DEFAULT)
        .build(),
      DecryptedMember.Builder()
        .aciBytes(Recipient.resolved(harness.others[0]).requireAci().toByteString())
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
      .aciBytes(harness.self.requireAci().toByteString())
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
}
