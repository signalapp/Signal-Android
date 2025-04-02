package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.ByteString
import org.junit.Test
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedString
import org.signal.storageservice.protos.groups.local.DecryptedTimer
import org.signal.storageservice.protos.groups.local.EnabledState
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.util.Util
import java.util.UUID

class GroupChangeReconstructTest {
  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   *
   *
   * If we didn't, newly added fields would not be detected by [GroupChangeReconstruct.reconstructGroupChange].
   */
  @Test
  fun ensure_GroupChangeReconstruct_knows_about_all_fields_of_DecryptedGroup() {
    val maxFieldFound = ProtobufTestUtils.getMaxDeclaredFieldNumber(DecryptedGroup::class.java)

    assertThat(
      actual = maxFieldFound,
      name = "GroupChangeReconstruct and its tests need updating to account for new fields on " + DecryptedGroup::class.java.name
    ).isEqualTo(13)
  }

  @Test
  fun empty_to_empty() {
    val from = DecryptedGroup.Builder().build()
    val to = DecryptedGroup.Builder().build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().build())
  }

  @Test
  fun revision_set_to_the_target() {
    val from = DecryptedGroup.Builder().revision(10).build()
    val to = DecryptedGroup.Builder().revision(20).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange.revision).isEqualTo(20)
  }

  @Test
  fun title_change() {
    val from = DecryptedGroup.Builder().title("A").build()
    val to = DecryptedGroup.Builder().title("B").build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newTitle(DecryptedString.Builder().value_("B").build()).build())
  }

  @Test
  fun description_change() {
    val from = DecryptedGroup.Builder().description("A").build()
    val to = DecryptedGroup.Builder().description("B").build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newDescription(DecryptedString.Builder().value_("B").build()).build())
  }

  @Test
  fun announcement_group_change() {
    val from = DecryptedGroup.Builder().isAnnouncementGroup(EnabledState.DISABLED).build()
    val to = DecryptedGroup.Builder().isAnnouncementGroup(EnabledState.ENABLED).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newIsAnnouncementGroup(EnabledState.ENABLED).build())
  }

  @Test
  fun avatar_change() {
    val from = DecryptedGroup.Builder().avatar("A").build()
    val to = DecryptedGroup.Builder().avatar("B").build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newAvatar(DecryptedString.Builder().value_("B").build()).build())
  }

  @Test
  fun timer_change() {
    val from = DecryptedGroup.Builder().disappearingMessagesTimer(DecryptedTimer.Builder().duration(100).build()).build()
    val to = DecryptedGroup.Builder().disappearingMessagesTimer(DecryptedTimer.Builder().duration(200).build()).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newTimer(DecryptedTimer.Builder().duration(200).build()).build())
  }

  @Test
  fun access_control_change_attributes() {
    val from = DecryptedGroup.Builder().accessControl(AccessControl.Builder().attributes(AccessControl.AccessRequired.MEMBER).build()).build()
    val to = DecryptedGroup.Builder().accessControl(AccessControl.Builder().attributes(AccessControl.AccessRequired.ADMINISTRATOR).build()).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR).build())
  }

  @Test
  fun access_control_change_membership() {
    val from = DecryptedGroup.Builder().accessControl(AccessControl.Builder().members(AccessControl.AccessRequired.ADMINISTRATOR).build()).build()
    val to = DecryptedGroup.Builder().accessControl(AccessControl.Builder().members(AccessControl.AccessRequired.MEMBER).build()).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newMemberAccess(AccessControl.AccessRequired.MEMBER).build())
  }

  @Test
  fun access_control_change_membership_and_attributes() {
    val from = DecryptedGroup.Builder().accessControl(
      AccessControl.Builder().members(AccessControl.AccessRequired.MEMBER)
        .attributes(AccessControl.AccessRequired.ADMINISTRATOR).build()
    ).build()
    val to = DecryptedGroup.Builder().accessControl(
      AccessControl.Builder().members(AccessControl.AccessRequired.ADMINISTRATOR)
        .attributes(AccessControl.AccessRequired.MEMBER).build()
    ).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(
      DecryptedGroupChange.Builder().newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .newAttributeAccess(AccessControl.AccessRequired.MEMBER).build()
    )
  }

  @Test
  fun new_member() {
    val uuidNew = UUID.randomUUID()
    val from = DecryptedGroup.Builder().build()
    val to = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.member(uuidNew))).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newMembers(listOf(ProtoTestUtils.member(uuidNew))).build())
  }

  @Test
  fun removed_member() {
    val uuidOld = UUID.randomUUID()
    val from = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.member(uuidOld))).build()
    val to = DecryptedGroup.Builder().build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().deleteMembers(listOf(UuidUtil.toByteString(uuidOld))).build())
  }

  @Test
  fun new_member_and_existing_member() {
    val uuidOld = UUID.randomUUID()
    val uuidNew = UUID.randomUUID()
    val from = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.member(uuidOld))).build()
    val to = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.member(uuidOld), ProtoTestUtils.member(uuidNew))).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newMembers(listOf(ProtoTestUtils.member(uuidNew))).build())
  }

  @Test
  fun removed_member_and_remaining_member() {
    val uuidOld = UUID.randomUUID()
    val uuidRemaining = UUID.randomUUID()
    val from = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.member(uuidOld), ProtoTestUtils.member(uuidRemaining))).build()
    val to = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.member(uuidRemaining))).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().deleteMembers(listOf(UuidUtil.toByteString(uuidOld))).build())
  }

  @Test
  fun new_member_by_invite() {
    val uuidNew = UUID.randomUUID()
    val from = DecryptedGroup.Builder().pendingMembers(listOf(ProtoTestUtils.pendingMember(uuidNew))).build()
    val to = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.member(uuidNew))).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().promotePendingMembers(listOf(ProtoTestUtils.member(uuidNew))).build())
  }

  @Test
  fun uninvited_member_by_invite() {
    val uuidNew = UUID.randomUUID()
    val from = DecryptedGroup.Builder().pendingMembers(listOf(ProtoTestUtils.pendingMember(uuidNew))).build()
    val to = DecryptedGroup.Builder().build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().deletePendingMembers(listOf(ProtoTestUtils.pendingMemberRemoval(uuidNew))).build())
  }

  @Test
  fun new_invite() {
    val uuidNew = UUID.randomUUID()
    val from = DecryptedGroup.Builder().build()
    val to = DecryptedGroup.Builder().pendingMembers(listOf(ProtoTestUtils.pendingMember(uuidNew))).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newPendingMembers(listOf(ProtoTestUtils.pendingMember(uuidNew))).build())
  }

  @Test
  fun to_admin() {
    val uuid = UUID.randomUUID()
    val profileKey = ProtoTestUtils.randomProfileKey()
    val from = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.withProfileKey(ProtoTestUtils.member(uuid), profileKey))).build()
    val to = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.withProfileKey(ProtoTestUtils.admin(uuid), profileKey))).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().modifyMemberRoles(listOf(ProtoTestUtils.promoteAdmin(uuid))).build())
  }

  @Test
  fun to_member() {
    val uuid = UUID.randomUUID()
    val profileKey = ProtoTestUtils.randomProfileKey()
    val from = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.withProfileKey(ProtoTestUtils.admin(uuid), profileKey))).build()
    val to = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.withProfileKey(ProtoTestUtils.member(uuid), profileKey))).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().modifyMemberRoles(listOf(ProtoTestUtils.demoteAdmin(uuid))).build())
  }

  @Test
  fun profile_key_change_member() {
    val uuid = UUID.randomUUID()
    val profileKey1 = ProtoTestUtils.randomProfileKey()
    val profileKey2 = ProtoTestUtils.randomProfileKey()
    val from = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.withProfileKey(ProtoTestUtils.admin(uuid), profileKey1))).build()
    val to = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.withProfileKey(ProtoTestUtils.admin(uuid), profileKey2))).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().modifiedProfileKeys(listOf(ProtoTestUtils.withProfileKey(ProtoTestUtils.admin(uuid), profileKey2))).build())
  }

  @Test
  fun new_invite_access() {
    val from = DecryptedGroup.Builder()
      .accessControl(
        AccessControl.Builder()
          .addFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR)
          .build()
      )
      .build()
    val to = DecryptedGroup.Builder()
      .accessControl(
        AccessControl.Builder()
          .addFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE)
          .build()
      )
      .build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(
      DecryptedGroupChange.Builder()
        .newInviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE)
        .build()
    )
  }

  @Test
  fun new_requesting_members() {
    val member1 = UUID.randomUUID()
    val profileKey1 = ProtoTestUtils.newProfileKey()
    val from = DecryptedGroup.Builder()
      .build()
    val to = DecryptedGroup.Builder()
      .requestingMembers(listOf(ProtoTestUtils.requestingMember(member1, profileKey1)))
      .build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(
      DecryptedGroupChange.Builder()
        .newRequestingMembers(listOf(ProtoTestUtils.requestingMember(member1, profileKey1)))
        .build()
    )
  }

  @Test
  fun new_requesting_members_ignores_existing_by_uuid() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val profileKey2 = ProtoTestUtils.newProfileKey()

    val from = DecryptedGroup.Builder()
      .requestingMembers(listOf(ProtoTestUtils.requestingMember(member1, ProtoTestUtils.newProfileKey())))
      .build()

    val to = DecryptedGroup.Builder()
      .requestingMembers(listOf(ProtoTestUtils.requestingMember(member1, ProtoTestUtils.newProfileKey()), ProtoTestUtils.requestingMember(member2, profileKey2)))
      .build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(
      DecryptedGroupChange.Builder()
        .newRequestingMembers(listOf(ProtoTestUtils.requestingMember(member2, profileKey2)))
        .build()
    )
  }

  @Test
  fun removed_requesting_members() {
    val member1 = UUID.randomUUID()
    val from = DecryptedGroup.Builder()
      .requestingMembers(listOf(ProtoTestUtils.requestingMember(member1, ProtoTestUtils.newProfileKey())))
      .build()
    val to = DecryptedGroup.Builder()
      .build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(
      DecryptedGroupChange.Builder()
        .deleteRequestingMembers(listOf(UuidUtil.toByteString(member1)))
        .build()
    )
  }

  @Test
  fun promote_requesting_members() {
    val member1 = UUID.randomUUID()
    val profileKey1 = ProtoTestUtils.newProfileKey()
    val member2 = UUID.randomUUID()
    val profileKey2 = ProtoTestUtils.newProfileKey()
    val from = DecryptedGroup.Builder()
      .requestingMembers(listOf(ProtoTestUtils.requestingMember(member1, profileKey1)))
      .requestingMembers(listOf(ProtoTestUtils.requestingMember(member2, profileKey2)))
      .build()
    val to = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1, profileKey1)))
      .members(listOf(ProtoTestUtils.admin(member2, profileKey2)))
      .build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(
      DecryptedGroupChange.Builder()
        .promoteRequestingMembers(listOf(ProtoTestUtils.approveMember(member1)))
        .promoteRequestingMembers(listOf(ProtoTestUtils.approveAdmin(member2)))
        .build()
    )
  }

  @Test
  fun new_invite_link_password() {
    val password1 = ByteString.of(*Util.getSecretBytes(16))
    val password2 = ByteString.of(*Util.getSecretBytes(16))
    val from = DecryptedGroup.Builder()
      .inviteLinkPassword(password1)
      .build()
    val to = DecryptedGroup.Builder()
      .inviteLinkPassword(password2)
      .build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(
      DecryptedGroupChange.Builder()
        .newInviteLinkPassword(password2)
        .build()
    )
  }

  @Test
  fun new_banned_member() {
    val uuidNew = UUID.randomUUID()
    val from = DecryptedGroup.Builder().build()
    val to = DecryptedGroup.Builder().bannedMembers(listOf(ProtoTestUtils.bannedMember(uuidNew))).build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().newBannedMembers(listOf(ProtoTestUtils.bannedMember(uuidNew))).build())
  }

  @Test
  fun removed_banned_member() {
    val uuidOld = UUID.randomUUID()
    val from = DecryptedGroup.Builder().bannedMembers(listOf(ProtoTestUtils.bannedMember(uuidOld))).build()
    val to = DecryptedGroup.Builder().build()

    val decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to)

    assertThat(decryptedGroupChange).isEqualTo(DecryptedGroupChange.Builder().deleteBannedMembers(listOf(ProtoTestUtils.bannedMember(uuidOld))).build())
  }
}
