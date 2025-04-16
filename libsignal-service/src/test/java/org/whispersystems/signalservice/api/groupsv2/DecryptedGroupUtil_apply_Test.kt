package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.ByteString
import org.junit.Test
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval
import org.signal.storageservice.protos.groups.local.DecryptedString
import org.signal.storageservice.protos.groups.local.DecryptedTimer
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.util.Util
import java.util.UUID

class DecryptedGroupUtil_apply_Test {
  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   *
   *
   * If we didn't, newly added fields would not be applied by [DecryptedGroupUtil.apply].
   */
  @Test
  fun ensure_DecryptedGroupUtil_knows_about_all_fields_of_DecryptedGroupChange() {
    val maxFieldFound = ProtobufTestUtils.getMaxDeclaredFieldNumber(DecryptedGroupChange::class.java)

    assertThat(
      actual = maxFieldFound,
      name = "DecryptedGroupUtil and its tests need updating to account for new fields on " + DecryptedGroupChange::class.java.name
    ).isEqualTo(24)
  }

  @Test
  fun apply_revision() {
    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(9)
        .build(),
      DecryptedGroupChange.Builder()
        .revision(10)
        .build()
    )
    assertThat(newGroup.revision).isEqualTo(10)
  }

  @Test
  fun apply_new_member() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val member2 = ProtoTestUtils.member(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newMembers(listOf(member2))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1, member2))
        .build()
    )
  }

  @Test
  fun apply_new_member_already_in_the_group() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val member2 = ProtoTestUtils.member(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1, member2))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newMembers(listOf(member2))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1, member2))
        .build()
    )
  }

  @Test
  fun apply_new_member_already_in_the_group_by_uuid() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val member2Uuid = UUID.randomUUID()
    val member2a = ProtoTestUtils.member(member2Uuid, ProtoTestUtils.newProfileKey())
    val member2b = ProtoTestUtils.member(member2Uuid, ProtoTestUtils.newProfileKey())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1, member2a))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newMembers(listOf(member2b))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1, member2b))
        .build()
    )
  }

  @Test
  fun apply_remove_member() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val member2 = ProtoTestUtils.member(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .members(listOf(member1, member2))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .deleteMembers(listOf(member1.aciBytes))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(14)
        .members(listOf(member2))
        .build()
    )
  }

  @Test
  fun apply_remove_members() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val member2 = ProtoTestUtils.member(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .members(listOf(member1, member2))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .deleteMembers(listOf(member1.aciBytes, member2.aciBytes))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(14)
        .build()
    )
  }

  @Test
  fun apply_remove_members_not_found() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val member2 = ProtoTestUtils.member(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .members(listOf(member1))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .deleteMembers(listOf(member2.aciBytes))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .members(listOf(member1))
        .revision(14)
        .build()
    )
  }

  @Test
  fun apply_modify_member_role() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val member2 = ProtoTestUtils.admin(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .members(listOf(member1, member2))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .modifyMemberRoles(
          listOf(
            DecryptedModifyMemberRole.Builder().aciBytes(member1.aciBytes).role(Member.Role.ADMINISTRATOR).build(),
            DecryptedModifyMemberRole.Builder().aciBytes(member2.aciBytes).role(Member.Role.DEFAULT).build()
          )
        )
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(14)
        .members(listOf(ProtoTestUtils.asAdmin(member1), ProtoTestUtils.asMember(member2)))
        .build()
    )
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException::class)
  fun not_able_to_apply_modify_member_role_for_non_member() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val member2 = ProtoTestUtils.member(UUID.randomUUID())

    DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .members(listOf(member1))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .modifyMemberRoles(
          listOf(
            DecryptedModifyMemberRole.Builder()
              .role(Member.Role.ADMINISTRATOR)
              .aciBytes(member2.aciBytes)
              .build()
          )
        )
        .build()
    )
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException::class)
  fun not_able_to_apply_modify_member_role_for_no_role() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())

    DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .members(listOf(member1))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .modifyMemberRoles(
          listOf(
            DecryptedModifyMemberRole.Builder()
              .aciBytes(member1.aciBytes)
              .build()
          )
        )
        .build()
    )
  }

  @Test
  fun apply_modify_member_profile_keys() {
    val profileKey1 = ProtoTestUtils.randomProfileKey()
    val profileKey2a = ProtoTestUtils.randomProfileKey()
    val profileKey2b = ProtoTestUtils.randomProfileKey()
    val member1 = ProtoTestUtils.member(UUID.randomUUID(), profileKey1)
    val member2a = ProtoTestUtils.member(UUID.randomUUID(), profileKey2a)
    val member2b = ProtoTestUtils.withProfileKey(member2a, profileKey2b)

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .members(listOf(member1, member2a))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .modifiedProfileKeys(listOf(member2b))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(14)
        .members(listOf(member1, member2b))
        .build()
    )
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException::class)
  fun cant_apply_modify_member_profile_keys_if_member_not_in_group() {
    val profileKey1 = ProtoTestUtils.randomProfileKey()
    val profileKey2a = ProtoTestUtils.randomProfileKey()
    val profileKey2b = ProtoTestUtils.randomProfileKey()
    val member1 = ProtoTestUtils.member(UUID.randomUUID(), profileKey1)
    val member2a = ProtoTestUtils.member(UUID.randomUUID(), profileKey2a)
    val member2b = ProtoTestUtils.member(UUID.randomUUID(), profileKey2b)

    DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .members(listOf(member1, member2a))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .modifiedProfileKeys(listOf(member2b))
        .build()
    )
  }

  @Test
  fun apply_modify_admin_profile_keys() {
    val adminUuid = UUID.randomUUID()
    val profileKey1 = ProtoTestUtils.randomProfileKey()
    val profileKey2a = ProtoTestUtils.randomProfileKey()
    val profileKey2b = ProtoTestUtils.randomProfileKey()
    val member1 = ProtoTestUtils.member(UUID.randomUUID(), profileKey1)
    val admin2a = ProtoTestUtils.admin(adminUuid, profileKey2a)

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .members(listOf<DecryptedMember>(member1, admin2a))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .modifiedProfileKeys(
          listOf(
            DecryptedMember.Builder()
              .aciBytes(UuidUtil.toByteString(adminUuid))
              .build()
              .newBuilder()
              .profileKey(ByteString.of(*profileKey2b.serialize()))
              .build()
          )
        )
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(14)
        .members(listOf(member1, ProtoTestUtils.admin(adminUuid, profileKey2b)))
        .build()
    )
  }

  @Test
  fun apply_new_pending_member() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val pending = ProtoTestUtils.pendingMember(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newPendingMembers(listOf(pending))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1))
        .pendingMembers(listOf(pending))
        .build()
    )
  }

  @Test
  fun apply_new_pending_member_already_pending() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val pending = ProtoTestUtils.pendingMember(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .pendingMembers(listOf(pending))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newPendingMembers(listOf(pending))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1))
        .pendingMembers(listOf(pending))
        .build()
    )
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException::class)
  fun apply_new_pending_member_already_in_group() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val uuid2 = UUID.randomUUID()
    val member2 = ProtoTestUtils.member(uuid2)
    val pending2 = ProtoTestUtils.pendingMember(uuid2)

    DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1, member2))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newPendingMembers(listOf(pending2))
        .build()
    )
  }

  @Test
  fun remove_pending_member() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val pendingUuid = UUID.randomUUID()
    val pending = ProtoTestUtils.pendingMember(pendingUuid)

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .pendingMembers(listOf(pending))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .deletePendingMembers(
          listOf(
            DecryptedPendingMemberRemoval.Builder()
              .serviceIdCipherText(ProtoTestUtils.encrypt(pendingUuid))
              .build()
          )
        )
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1))
        .build()
    )
  }

  @Test
  fun cannot_remove_pending_member_if_not_in_group() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val pendingUuid = UUID.randomUUID()

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .deletePendingMembers(
          listOf(
            DecryptedPendingMemberRemoval.Builder()
              .serviceIdCipherText(ProtoTestUtils.encrypt(pendingUuid))
              .build()
          )
        )
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1))
        .build()
    )
  }

  @Test
  fun promote_pending_member() {
    val profileKey2 = ProtoTestUtils.randomProfileKey()
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val pending2Uuid = UUID.randomUUID()
    val pending2 = ProtoTestUtils.pendingMember(pending2Uuid)
    val member2 = ProtoTestUtils.member(pending2Uuid, profileKey2)

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .pendingMembers(listOf(pending2))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .promotePendingMembers(listOf(member2))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1, member2))
        .build()
    )
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException::class)
  fun cannot_promote_pending_member_if_not_in_group() {
    val profileKey2 = ProtoTestUtils.randomProfileKey()
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val pending2Uuid = UUID.randomUUID()
    val member2 = ProtoTestUtils.withProfileKey(ProtoTestUtils.admin(pending2Uuid), profileKey2)

    DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .promotePendingMembers(listOf(member2))
        .build()
    )
  }

  @Test
  fun skip_promote_pending_member_by_direct_add() {
    val profileKey2 = ProtoTestUtils.randomProfileKey()
    val profileKey3 = ProtoTestUtils.randomProfileKey()
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val pending2Uuid = UUID.randomUUID()
    val pending3Uuid = UUID.randomUUID()
    val pending4Uuid = UUID.randomUUID()
    val pending2 = ProtoTestUtils.pendingMember(pending2Uuid)
    val pending3 = ProtoTestUtils.pendingMember(pending3Uuid)
    val pending4 = ProtoTestUtils.pendingMember(pending4Uuid)
    val member2 = ProtoTestUtils.member(pending2Uuid, profileKey2)
    val member3 = ProtoTestUtils.member(pending3Uuid, profileKey3)

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .pendingMembers(listOf(pending2, pending3, pending4))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newMembers(listOf(member2, member3))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1, member2, member3))
        .pendingMembers(listOf(pending4))
        .build()
    )
  }

  @Test
  fun skip_promote_requesting_member_by_direct_add() {
    val profileKey2 = ProtoTestUtils.randomProfileKey()
    val profileKey3 = ProtoTestUtils.randomProfileKey()
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val requesting2Uuid = UUID.randomUUID()
    val requesting3Uuid = UUID.randomUUID()
    val requesting4Uuid = UUID.randomUUID()
    val requesting2 = ProtoTestUtils.requestingMember(requesting2Uuid)
    val requesting3 = ProtoTestUtils.requestingMember(requesting3Uuid)
    val requesting4 = ProtoTestUtils.requestingMember(requesting4Uuid)
    val member2 = ProtoTestUtils.member(requesting2Uuid, profileKey2)
    val member3 = ProtoTestUtils.member(requesting3Uuid, profileKey3)

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .requestingMembers(listOf(requesting2, requesting3, requesting4))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newMembers(listOf(member2, member3))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1, member2, member3))
        .requestingMembers(listOf(requesting4))
        .build()
    )
  }

  @Test
  fun title() {
    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .title("Old title")
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newTitle(DecryptedString.Builder().value_("New title").build())
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .title("New title")
        .build()
    )
  }

  @Test
  fun description() {
    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .description("Old description")
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newDescription(DecryptedString.Builder().value_("New Description").build())
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .description("New Description")
        .build()
    )
  }

  @Test
  fun avatar() {
    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .avatar("https://cnd/oldavatar")
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newAvatar(DecryptedString.Builder().value_("https://cnd/newavatar").build())
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .avatar("https://cnd/newavatar")
        .build()
    )
  }

  @Test
  fun timer() {
    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .disappearingMessagesTimer(DecryptedTimer.Builder().duration(100).build())
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newTimer(DecryptedTimer.Builder().duration(2000).build())
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .disappearingMessagesTimer(DecryptedTimer.Builder().duration(2000).build())
        .build()
    )
  }

  @Test
  fun attribute_access() {
    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .accessControl(
          AccessControl.Builder()
            .attributes(AccessControl.AccessRequired.ADMINISTRATOR)
            .members(AccessControl.AccessRequired.MEMBER)
            .build()
        )
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newAttributeAccess(AccessControl.AccessRequired.MEMBER)
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .accessControl(
          AccessControl.Builder()
            .attributes(AccessControl.AccessRequired.MEMBER)
            .members(AccessControl.AccessRequired.MEMBER)
            .build()
        )
        .build()
    )
  }

  @Test
  fun membership_access() {
    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .accessControl(
          AccessControl.Builder()
            .attributes(AccessControl.AccessRequired.ADMINISTRATOR)
            .members(AccessControl.AccessRequired.MEMBER)
            .build()
        )
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .accessControl(
          AccessControl.Builder()
            .attributes(AccessControl.AccessRequired.ADMINISTRATOR)
            .members(AccessControl.AccessRequired.ADMINISTRATOR)
            .build()
        )
        .build()
    )
  }

  @Test
  fun change_both_access_levels() {
    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .accessControl(
          AccessControl.Builder()
            .attributes(AccessControl.AccessRequired.ADMINISTRATOR)
            .members(AccessControl.AccessRequired.MEMBER)
            .build()
        )
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newAttributeAccess(AccessControl.AccessRequired.MEMBER)
        .newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .accessControl(
          AccessControl.Builder()
            .attributes(AccessControl.AccessRequired.MEMBER)
            .members(AccessControl.AccessRequired.ADMINISTRATOR)
            .build()
        )
        .build()
    )
  }

  @Test
  fun invite_link_access() {
    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .accessControl(
          AccessControl.Builder()
            .attributes(AccessControl.AccessRequired.MEMBER)
            .members(AccessControl.AccessRequired.MEMBER)
            .addFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE)
            .build()
        )
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .accessControl(
          AccessControl.Builder()
            .attributes(AccessControl.AccessRequired.MEMBER)
            .members(AccessControl.AccessRequired.MEMBER)
            .addFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR)
            .build()
        )
        .build()
    )
  }

  @Test
  fun apply_new_requesting_member() {
    val member1 = ProtoTestUtils.requestingMember(UUID.randomUUID())
    val member2 = ProtoTestUtils.requestingMember(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .requestingMembers(listOf(member1))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newRequestingMembers(listOf(member2))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .requestingMembers(listOf(member1, member2))
        .build()
    )
  }

  @Test
  fun apply_remove_requesting_member() {
    val member1 = ProtoTestUtils.requestingMember(UUID.randomUUID())
    val member2 = ProtoTestUtils.requestingMember(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .requestingMembers(listOf(member1, member2))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .deleteRequestingMembers(listOf(member1.aciBytes))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(14)
        .requestingMembers(listOf(member2))
        .build()
    )
  }

  @Test
  fun promote_requesting_member() {
    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()
    val uuid3 = UUID.randomUUID()
    val profileKey1 = ProtoTestUtils.newProfileKey()
    val profileKey2 = ProtoTestUtils.newProfileKey()
    val profileKey3 = ProtoTestUtils.newProfileKey()
    val member1 = ProtoTestUtils.requestingMember(uuid1, profileKey1)
    val member2 = ProtoTestUtils.requestingMember(uuid2, profileKey2)
    val member3 = ProtoTestUtils.requestingMember(uuid3, profileKey3)

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .requestingMembers(listOf(member1, member2, member3))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .promoteRequestingMembers(
          listOf(
            DecryptedApproveMember.Builder()
              .role(Member.Role.DEFAULT)
              .aciBytes(member1.aciBytes)
              .build(),
            DecryptedApproveMember.Builder()
              .role(Member.Role.ADMINISTRATOR)
              .aciBytes(member2.aciBytes)
              .build()
          )
        )
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(14)
        .members(listOf(ProtoTestUtils.member(uuid1, profileKey1), ProtoTestUtils.admin(uuid2, profileKey2)))
        .requestingMembers(listOf(member3))
        .build()
    )
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException::class)
  fun cannot_apply_promote_requesting_member_without_a_role() {
    val uuid = UUID.randomUUID()
    val member = ProtoTestUtils.requestingMember(uuid)

    DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(13)
        .requestingMembers(listOf(member))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(14)
        .promoteRequestingMembers(listOf(DecryptedApproveMember.Builder().aciBytes(member.aciBytes).build()))
        .build()
    )
  }

  @Test
  fun invite_link_password() {
    val password1 = ByteString.of(*Util.getSecretBytes(16))
    val password2 = ByteString.of(*Util.getSecretBytes(16))

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .inviteLinkPassword(password1)
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newInviteLinkPassword(password2)
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .inviteLinkPassword(password2)
        .build()
    )
  }

  @Test
  fun invite_link_password_not_changed() {
    val password = ByteString.of(*Util.getSecretBytes(16))

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .inviteLinkPassword(password)
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .inviteLinkPassword(password)
        .build()
    )
  }

  @Test
  fun apply_new_banned_member() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val banned = ProtoTestUtils.bannedMember(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newBannedMembers(listOf(banned))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1))
        .bannedMembers(listOf(banned))
        .build()
    )
  }

  @Test
  fun apply_new_banned_member_already_banned() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val banned = ProtoTestUtils.bannedMember(UUID.randomUUID())

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .bannedMembers(listOf(banned))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .newBannedMembers(listOf(banned))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1))
        .bannedMembers(listOf(banned))
        .build()
    )
  }

  @Test
  fun remove_banned_member() {
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val bannedUuid = UUID.randomUUID()
    val banned = ProtoTestUtils.bannedMember(bannedUuid)

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .bannedMembers(listOf(banned))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .deleteBannedMembers(
          listOf(
            DecryptedBannedMember.Builder()
              .serviceIdBytes(UuidUtil.toByteString(bannedUuid))
              .build()
          )
        )
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1))
        .build()
    )
  }

  @Test
  fun promote_pending_member_pni_aci() {
    val profileKey2 = ProtoTestUtils.randomProfileKey()
    val member1 = ProtoTestUtils.member(UUID.randomUUID())
    val pending2Aci = UUID.randomUUID()
    val pending2Pni = UUID.randomUUID()
    val pending2 = ProtoTestUtils.pendingMember(pending2Pni)
    val member2 = ProtoTestUtils.pendingPniAciMember(pending2Aci, pending2Pni, profileKey2)

    val newGroup = DecryptedGroupUtil.apply(
      DecryptedGroup.Builder()
        .revision(10)
        .members(listOf(member1))
        .pendingMembers(listOf(pending2))
        .build(),
      DecryptedGroupChange.Builder()
        .revision(11)
        .promotePendingPniAciMembers(listOf(member2))
        .build()
    )

    assertThat(newGroup).isEqualTo(
      DecryptedGroup.Builder()
        .revision(11)
        .members(listOf(member1, member2))
        .build()
    )
  }
}
