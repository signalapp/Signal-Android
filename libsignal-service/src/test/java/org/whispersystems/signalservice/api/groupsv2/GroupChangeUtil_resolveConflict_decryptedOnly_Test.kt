package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
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

class GroupChangeUtil_resolveConflict_decryptedOnly_Test {
  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   *
   *
   * If we didn't, newly added fields would not be resolved by [GroupChangeUtil.resolveConflict].
   */
  @Test
  fun ensure_resolveConflict_knows_about_all_fields_of_DecryptedGroupChange() {
    val maxFieldFound = ProtobufTestUtils.getMaxDeclaredFieldNumber(DecryptedGroupChange::class.java)

    assertThat(
      actual = maxFieldFound,
      name = "GroupChangeUtil#resolveConflict and its tests need updating to account for new fields on " + DecryptedGroupChange::class.java.name
    ).isEqualTo(24)
  }

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   *
   *
   * If we didn't, newly added fields would not be resolved by [GroupChangeUtil.resolveConflict].
   */
  @Test
  fun ensure_resolveConflict_knows_about_all_fields_of_DecryptedGroup() {
    val maxFieldFound = ProtobufTestUtils.getMaxDeclaredFieldNumber(DecryptedGroup::class.java)

    assertThat(
      actual = maxFieldFound,
      name = "GroupChangeUtil#resolveConflict and its tests need updating to account for new fields on " + DecryptedGroup::class.java.name
    ).isEqualTo(13)
  }

  @Test
  fun field_3__changes_to_add_existing_members_are_excluded() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1), ProtoTestUtils.member(member3)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newMembers(listOf(ProtoTestUtils.member(member1), ProtoTestUtils.member(member2), ProtoTestUtils.member(member3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .newMembers(listOf(ProtoTestUtils.member(member2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_4__changes_to_remove_missing_members_are_excluded() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member2)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .deleteMembers(listOf(UuidUtil.toByteString(member1), UuidUtil.toByteString(member2), UuidUtil.toByteString(member3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .deleteMembers(listOf(UuidUtil.toByteString(member2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_5__role_change_is_preserved() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.admin(member1), ProtoTestUtils.member(member2), ProtoTestUtils.member(member3)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .modifyMemberRoles(listOf(ProtoTestUtils.demoteAdmin(member1), ProtoTestUtils.promoteAdmin(member2)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(resolvedChanges).isEqualTo(decryptedChange)
  }

  @Test
  fun field_5__unnecessary_role_changes_removed() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()
    val memberNotInGroup = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.admin(member1), ProtoTestUtils.member(member2), ProtoTestUtils.member(member3)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .modifyMemberRoles(listOf(ProtoTestUtils.promoteAdmin(member1), ProtoTestUtils.promoteAdmin(member2), ProtoTestUtils.demoteAdmin(member3), ProtoTestUtils.promoteAdmin(memberNotInGroup)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .modifyMemberRoles(listOf(ProtoTestUtils.promoteAdmin(member2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_6__profile_key_changes() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()
    val memberNotInGroup = UUID.randomUUID()
    val profileKey1 = ProtoTestUtils.randomProfileKey()
    val profileKey2 = ProtoTestUtils.randomProfileKey()
    val profileKey3 = ProtoTestUtils.randomProfileKey()
    val profileKey4 = ProtoTestUtils.randomProfileKey()
    val profileKey2b = ProtoTestUtils.randomProfileKey()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1, profileKey1), ProtoTestUtils.member(member2, profileKey2), ProtoTestUtils.member(member3, profileKey3)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .modifiedProfileKeys(listOf(ProtoTestUtils.member(member1, profileKey1), ProtoTestUtils.member(member2, profileKey2b), ProtoTestUtils.member(member3, profileKey3), ProtoTestUtils.member(memberNotInGroup, profileKey4)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .modifiedProfileKeys(listOf(ProtoTestUtils.member(member2, profileKey2b)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_7__add_pending_members() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1)))
      .pendingMembers(listOf(ProtoTestUtils.pendingMember(member3)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newPendingMembers(listOf(ProtoTestUtils.pendingMember(member1), ProtoTestUtils.pendingMember(member2), ProtoTestUtils.pendingMember(member3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .newPendingMembers(listOf(ProtoTestUtils.pendingMember(member2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_8__delete_pending_members() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1)))
      .pendingMembers(listOf(ProtoTestUtils.pendingMember(member2)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .deletePendingMembers(listOf(ProtoTestUtils.pendingMemberRemoval(member1), ProtoTestUtils.pendingMemberRemoval(member2), ProtoTestUtils.pendingMemberRemoval(member3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .deletePendingMembers(listOf(ProtoTestUtils.pendingMemberRemoval(member2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_9__promote_pending_members() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()
    val profileKey2 = ProtoTestUtils.randomProfileKey()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1)))
      .pendingMembers(listOf(ProtoTestUtils.pendingMember(member2)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .promotePendingMembers(listOf(ProtoTestUtils.member(member1), ProtoTestUtils.member(member2, profileKey2), ProtoTestUtils.member(member3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .promotePendingMembers(listOf(ProtoTestUtils.member(member2, profileKey2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_3_to_9__add_of_pending_member_converted_to_a_promote() {
    val member1 = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .pendingMembers(listOf(ProtoTestUtils.pendingMember(member1)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newMembers(listOf(ProtoTestUtils.member(member1)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .promotePendingMembers(listOf(ProtoTestUtils.member(member1)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_10__title_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .title("Existing title")
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newTitle(DecryptedString.Builder().value_("New title").build())
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(resolvedChanges).isEqualTo(decryptedChange)
  }

  @Test
  fun field_10__no_title_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .title("Existing title")
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newTitle(DecryptedString.Builder().value_("Existing title").build())
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(resolvedChanges)).isTrue()
  }

  @Test
  fun field_11__avatar_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .avatar("Existing avatar")
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newAvatar(DecryptedString.Builder().value_("New avatar").build())
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(resolvedChanges).isEqualTo(decryptedChange)
  }

  @Test
  fun field_11__no_avatar_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .avatar("Existing avatar")
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newAvatar(DecryptedString.Builder().value_("Existing avatar").build())
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(resolvedChanges)).isTrue()
  }

  @Test
  fun field_12__timer_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .disappearingMessagesTimer(DecryptedTimer.Builder().duration(123).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newTimer(DecryptedTimer.Builder().duration(456).build())
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(resolvedChanges).isEqualTo(decryptedChange)
  }

  @Test
  fun field_12__no_timer_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .disappearingMessagesTimer(DecryptedTimer.Builder().duration(123).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newTimer(DecryptedTimer.Builder().duration(123).build())
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(resolvedChanges)).isTrue()
  }

  @Test
  fun field_13__attribute_access_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .accessControl(AccessControl.Builder().attributes(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newAttributeAccess(AccessControl.AccessRequired.MEMBER)
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(resolvedChanges).isEqualTo(decryptedChange)
  }

  @Test
  fun field_13__no_attribute_access_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .accessControl(AccessControl.Builder().attributes(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(resolvedChanges)).isTrue()
  }

  @Test
  fun field_14__membership_access_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .accessControl(AccessControl.Builder().members(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newMemberAccess(AccessControl.AccessRequired.MEMBER)
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(resolvedChanges).isEqualTo(decryptedChange)
  }

  @Test
  fun field_14__no_membership_access_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .accessControl(AccessControl.Builder().members(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(resolvedChanges)).isTrue()
  }

  @Test
  fun field_15__no_membership_access_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .accessControl(AccessControl.Builder().addFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(resolvedChanges)).isTrue()
  }

  @Test
  fun field_16__changes_to_add_requesting_members_when_full_members_are_removed() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()
    val profileKey2 = ProtoTestUtils.randomProfileKey()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1), ProtoTestUtils.member(member3)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newRequestingMembers(listOf(ProtoTestUtils.requestingMember(member1), ProtoTestUtils.requestingMember(member2, profileKey2), ProtoTestUtils.requestingMember(member3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .newRequestingMembers(listOf(ProtoTestUtils.requestingMember(member2, profileKey2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_16__changes_to_add_requesting_members_when_pending_are_promoted() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()
    val profileKey1 = ProtoTestUtils.randomProfileKey()
    val profileKey2 = ProtoTestUtils.randomProfileKey()
    val profileKey3 = ProtoTestUtils.randomProfileKey()

    val groupState = DecryptedGroup.Builder()
      .pendingMembers(listOf(ProtoTestUtils.pendingMember(member1), ProtoTestUtils.pendingMember(member3)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newRequestingMembers(listOf(ProtoTestUtils.requestingMember(member1, profileKey1), ProtoTestUtils.requestingMember(member2, profileKey2), ProtoTestUtils.requestingMember(member3, profileKey3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .promotePendingMembers(listOf(ProtoTestUtils.member(member3, profileKey3), ProtoTestUtils.member(member1, profileKey1)))
      .newRequestingMembers(listOf(ProtoTestUtils.requestingMember(member2, profileKey2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_17__changes_to_remove_missing_requesting_members_are_excluded() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .requestingMembers(listOf(ProtoTestUtils.requestingMember(member2)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .deleteRequestingMembers(listOf(UuidUtil.toByteString(member1), UuidUtil.toByteString(member2), UuidUtil.toByteString(member3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .deleteRequestingMembers(listOf(UuidUtil.toByteString(member2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_18__promote_requesting_members() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1)))
      .requestingMembers(listOf(ProtoTestUtils.requestingMember(member2)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .promoteRequestingMembers(listOf(ProtoTestUtils.approveMember(member1), ProtoTestUtils.approveMember(member2), ProtoTestUtils.approveMember(member3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .promoteRequestingMembers(listOf(ProtoTestUtils.approveMember(member2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_19__password_change_is_kept() {
    val password1 = ByteString.of(*Util.getSecretBytes(16))
    val password2 = ByteString.of(*Util.getSecretBytes(16))

    val groupState = DecryptedGroup.Builder()
      .inviteLinkPassword(password1)
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newInviteLinkPassword(password2)
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(resolvedChanges).isEqualTo(decryptedChange)
  }

  @Test
  fun field_20__description_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .description("Existing description")
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newDescription(DecryptedString.Builder().value_("New description").build())
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(resolvedChanges).isEqualTo(decryptedChange)
  }

  @Test
  fun field_20__no_description_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .description("Existing description")
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newDescription(DecryptedString.Builder().value_("Existing description").build())
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(resolvedChanges)).isTrue()
  }

  @Test
  fun field_21__announcement_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .isAnnouncementGroup(EnabledState.DISABLED)
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newIsAnnouncementGroup(EnabledState.ENABLED)
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(resolvedChanges).isEqualTo(decryptedChange)
  }

  @Test
  fun field_21__no_announcement_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .isAnnouncementGroup(EnabledState.ENABLED)
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newIsAnnouncementGroup(EnabledState.ENABLED)
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(resolvedChanges)).isTrue()
  }

  @Test
  fun field_22__add_banned_members() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1)))
      .bannedMembers(listOf(ProtoTestUtils.bannedMember(member3)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newBannedMembers(listOf(ProtoTestUtils.bannedMember(member1), ProtoTestUtils.bannedMember(member2), ProtoTestUtils.bannedMember(member3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .newBannedMembers(listOf(ProtoTestUtils.bannedMember(member1), ProtoTestUtils.bannedMember(member2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_23__delete_banned_members() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1)))
      .bannedMembers(listOf(ProtoTestUtils.bannedMember(member2)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .deleteBannedMembers(listOf(ProtoTestUtils.bannedMember(member1), ProtoTestUtils.bannedMember(member2), ProtoTestUtils.bannedMember(member3)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .deleteBannedMembers(listOf(ProtoTestUtils.bannedMember(member2)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }

  @Test
  fun field_24__promote_pending_members() {
    val member1 = ProtoTestUtils.pendingPniAciMember(UUID.randomUUID(), UUID.randomUUID(), ProtoTestUtils.randomProfileKey())
    val member2 = ProtoTestUtils.pendingPniAciMember(UUID.randomUUID(), UUID.randomUUID(), ProtoTestUtils.randomProfileKey())

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(UuidUtil.fromByteString(member1.aciBytes))))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .promotePendingPniAciMembers(listOf(ProtoTestUtils.pendingPniAciMember(member1.aciBytes, member1.pniBytes, member1.profileKey), ProtoTestUtils.pendingPniAciMember(member2.aciBytes, member2.pniBytes, member2.profileKey)))
      .build()

    val resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build()

    val expected = DecryptedGroupChange.Builder()
      .promotePendingPniAciMembers(listOf(ProtoTestUtils.pendingPniAciMember(member2.aciBytes, member2.pniBytes, member2.profileKey)))
      .build()

    assertThat(resolvedChanges).isEqualTo(expected)
  }
}
