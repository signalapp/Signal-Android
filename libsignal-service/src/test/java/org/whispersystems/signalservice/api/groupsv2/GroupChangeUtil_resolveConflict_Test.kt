package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import okio.ByteString
import org.junit.Test
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.BannedMember
import org.signal.storageservice.protos.groups.GroupChange
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddBannedMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddPendingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddRequestingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeleteBannedMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeleteMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeletePendingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeleteRequestingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyMemberProfileKeyAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyMemberRoleAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.PromotePendingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.PromotePendingPniAciMemberProfileKeyAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.PromoteRequestingMemberAction
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.PendingMember
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedString
import org.signal.storageservice.protos.groups.local.DecryptedTimer
import org.signal.storageservice.protos.groups.local.EnabledState
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.util.Util
import java.util.UUID

class GroupChangeUtil_resolveConflict_Test {
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
  fun ensure_resolveConflict_knows_about_all_fields_of_GroupChange() {
    val maxFieldFound = ProtobufTestUtils.getMaxDeclaredFieldNumber(DecryptedGroupChange::class.java)

    assertThat(
      actual = maxFieldFound,
      name = "GroupChangeUtil#resolveConflict and its tests need updating to account for new fields on " + GroupChange::class.java.name
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
  fun empty_actions() {
    val resolvedActions = GroupChangeUtil.resolveConflict(
      DecryptedGroup.Builder().build(),
      DecryptedGroupChange.Builder().build(),
      GroupChange.Actions.Builder().build()
    )
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(resolvedActions)).isTrue()
  }

  @Test
  fun field_3__changes_to_add_existing_members_are_excluded() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()
    val profileKey2 = ProtoTestUtils.randomProfileKey()
    val groupState = DecryptedGroup.Builder().members(listOf(ProtoTestUtils.member(member1), ProtoTestUtils.member(member3))).build()
    val decryptedChange = DecryptedGroupChange.Builder().newMembers(listOf(ProtoTestUtils.member(member1), ProtoTestUtils.member(member2), ProtoTestUtils.member(member3))).build()

    val change = GroupChange.Actions.Builder()
      .addMembers(
        listOf(
          AddMemberAction.Builder().added(ProtoTestUtils.encryptedMember(member1, ProtoTestUtils.randomProfileKey())).build(),
          AddMemberAction.Builder().added(ProtoTestUtils.encryptedMember(member2, profileKey2)).build(),
          AddMemberAction.Builder().added(ProtoTestUtils.encryptedMember(member3, ProtoTestUtils.randomProfileKey())).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .addMembers(listOf(AddMemberAction.Builder().added(ProtoTestUtils.encryptedMember(member2, profileKey2)).build()))
      .build()
    assertThat(resolvedActions).isEqualTo(expected)
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

    val change = GroupChange.Actions.Builder()
      .deleteMembers(
        listOf(
          DeleteMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member1)).build(),
          DeleteMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member2)).build(),
          DeleteMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member3)).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .deleteMembers(listOf(DeleteMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member2)).build()))
      .build()
    assertThat(resolvedActions).isEqualTo(expected)
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

    val change = GroupChange.Actions.Builder()
      .modifyMemberRoles(
        listOf(
          ModifyMemberRoleAction.Builder().userId(ProtoTestUtils.encrypt(member1)).role(Member.Role.DEFAULT).build(),
          ModifyMemberRoleAction.Builder().userId(ProtoTestUtils.encrypt(member2)).role(Member.Role.ADMINISTRATOR).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(resolvedActions).isEqualTo(change)
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

    val change = GroupChange.Actions.Builder()
      .modifyMemberRoles(
        listOf(
          ModifyMemberRoleAction.Builder().userId(ProtoTestUtils.encrypt(member1)).role(Member.Role.ADMINISTRATOR).build(),
          ModifyMemberRoleAction.Builder().userId(ProtoTestUtils.encrypt(member2)).role(Member.Role.ADMINISTRATOR).build(),
          ModifyMemberRoleAction.Builder().userId(ProtoTestUtils.encrypt(member3)).role(Member.Role.DEFAULT).build(),
          ModifyMemberRoleAction.Builder().userId(ProtoTestUtils.encrypt(memberNotInGroup)).role(Member.Role.ADMINISTRATOR).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .modifyMemberRoles(listOf(ModifyMemberRoleAction.Builder().userId(ProtoTestUtils.encrypt(member2)).role(Member.Role.ADMINISTRATOR).build()))
      .build()
    assertThat(resolvedActions).isEqualTo(expected)
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

    val change = GroupChange.Actions.Builder()
      .modifyMemberProfileKeys(
        listOf(
          ModifyMemberProfileKeyAction.Builder().presentation(ProtoTestUtils.presentation(member1, profileKey1)).build(),
          ModifyMemberProfileKeyAction.Builder().presentation(ProtoTestUtils.presentation(member2, profileKey2b)).build(),
          ModifyMemberProfileKeyAction.Builder().presentation(ProtoTestUtils.presentation(member3, profileKey3)).build(),
          ModifyMemberProfileKeyAction.Builder().presentation(ProtoTestUtils.presentation(memberNotInGroup, profileKey4)).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .modifyMemberProfileKeys(listOf(ModifyMemberProfileKeyAction.Builder().presentation(ProtoTestUtils.presentation(member2, profileKey2b)).build()))
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
  }

  @Test
  fun field_7__add_pending_members() {
    val member1 = UUID.randomUUID()
    val member2 = UUID.randomUUID()
    val member3 = UUID.randomUUID()
    val profileKey2 = ProtoTestUtils.randomProfileKey()

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(member1)))
      .pendingMembers(listOf(ProtoTestUtils.pendingMember(member3)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newPendingMembers(listOf(ProtoTestUtils.pendingMember(member1), ProtoTestUtils.pendingMember(member2), ProtoTestUtils.pendingMember(member3)))
      .build()

    val change = GroupChange.Actions.Builder()
      .addPendingMembers(
        listOf(
          AddPendingMemberAction.Builder().added(PendingMember.Builder().member(ProtoTestUtils.encryptedMember(member1, ProtoTestUtils.randomProfileKey())).build()).build(),
          AddPendingMemberAction.Builder().added(PendingMember.Builder().member(ProtoTestUtils.encryptedMember(member2, profileKey2)).build()).build(),
          AddPendingMemberAction.Builder().added(PendingMember.Builder().member(ProtoTestUtils.encryptedMember(member3, ProtoTestUtils.randomProfileKey())).build()).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .addPendingMembers(listOf(AddPendingMemberAction.Builder().added(PendingMember.Builder().member(ProtoTestUtils.encryptedMember(member2, profileKey2)).build()).build()))
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
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

    val change = GroupChange.Actions.Builder()
      .deletePendingMembers(
        listOf(
          DeletePendingMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member1)).build(),
          DeletePendingMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member2)).build(),
          DeletePendingMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member3)).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .deletePendingMembers(listOf(DeletePendingMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member2)).build()))
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
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
      .promotePendingMembers(listOf(ProtoTestUtils.member(member1), ProtoTestUtils.member(member2), ProtoTestUtils.member(member3)))
      .build()

    val change = GroupChange.Actions.Builder()
      .promotePendingMembers(
        listOf(
          PromotePendingMemberAction.Builder().presentation(ProtoTestUtils.presentation(member1, ProtoTestUtils.randomProfileKey())).build(),
          PromotePendingMemberAction.Builder().presentation(ProtoTestUtils.presentation(member2, profileKey2)).build(),
          PromotePendingMemberAction.Builder().presentation(ProtoTestUtils.presentation(member3, ProtoTestUtils.randomProfileKey())).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .promotePendingMembers(listOf(PromotePendingMemberAction.Builder().presentation(ProtoTestUtils.presentation(member2, profileKey2)).build()))
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
  }

  @Test
  fun field_3_to_9__add_of_pending_member_converted_to_a_promote() {
    val member1 = UUID.randomUUID()
    val profileKey1 = ProtoTestUtils.randomProfileKey()

    val groupState = DecryptedGroup.Builder()
      .pendingMembers(listOf(ProtoTestUtils.pendingMember(member1)))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newMembers(listOf(ProtoTestUtils.member(member1)))
      .build()

    val change = GroupChange.Actions.Builder()
      .addMembers(listOf(AddMemberAction.Builder().added(ProtoTestUtils.encryptedMember(member1, profileKey1)).build()))
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .promotePendingMembers(listOf(PromotePendingMemberAction.Builder().presentation(ProtoTestUtils.presentation(member1, profileKey1)).build()))
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
  }

  @Test
  fun field_10__title_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .title("Existing title")
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newTitle(DecryptedString.Builder().value_("New title").build())
      .build()

    val change = GroupChange.Actions.Builder()
      .modifyTitle(GroupChange.Actions.ModifyTitleAction.Builder().title(ByteString.of(*"New title encrypted".toByteArray())).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(resolvedActions).isEqualTo(change)
  }

  @Test
  fun field_10__no_title_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .title("Existing title")
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newTitle(DecryptedString.Builder().value_("Existing title").build())
      .build()

    val change = GroupChange.Actions.Builder()
      .modifyTitle(GroupChange.Actions.ModifyTitleAction.Builder().title(ByteString.of(*"Existing title encrypted".toByteArray())).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(GroupChangeUtil.changeIsEmpty(resolvedActions)).isTrue()
  }

  @Test
  fun field_11__avatar_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .avatar("Existing avatar")
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newAvatar(DecryptedString.Builder().value_("New avatar").build())
      .build()

    val change = GroupChange.Actions.Builder()
      .modifyAvatar(GroupChange.Actions.ModifyAvatarAction.Builder().avatar("New avatar possibly encrypted").build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(resolvedActions).isEqualTo(change)
  }

  @Test
  fun field_11__no_avatar_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .avatar("Existing avatar")
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newAvatar(DecryptedString.Builder().value_("Existing avatar").build())
      .build()

    val change = GroupChange.Actions.Builder()
      .modifyAvatar(GroupChange.Actions.ModifyAvatarAction.Builder().avatar("Existing avatar possibly encrypted").build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(GroupChangeUtil.changeIsEmpty(resolvedActions)).isTrue()
  }

  @Test
  fun field_12__timer_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .disappearingMessagesTimer(DecryptedTimer.Builder().duration(123).build())
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newTimer(DecryptedTimer.Builder().duration(456).build())
      .build()

    val change = GroupChange.Actions.Builder()
      .modifyDisappearingMessagesTimer(GroupChange.Actions.ModifyDisappearingMessagesTimerAction.Builder().timer(ByteString.EMPTY).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(resolvedActions).isEqualTo(change)
  }

  @Test
  fun field_12__no_timer_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .disappearingMessagesTimer(DecryptedTimer.Builder().duration(123).build())
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newTimer(DecryptedTimer.Builder().duration(123).build())
      .build()

    val change = GroupChange.Actions.Builder()
      .modifyDisappearingMessagesTimer(GroupChange.Actions.ModifyDisappearingMessagesTimerAction.Builder().timer(ByteString.EMPTY).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(GroupChangeUtil.changeIsEmpty(resolvedActions)).isTrue()
  }

  @Test
  fun field_13__attribute_access_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .accessControl(AccessControl.Builder().attributes(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newAttributeAccess(AccessControl.AccessRequired.MEMBER)
      .build()
    val change = GroupChange.Actions.Builder()
      .modifyAttributesAccess(GroupChange.Actions.ModifyAttributesAccessControlAction.Builder().attributesAccess(AccessControl.AccessRequired.MEMBER).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(resolvedActions).isEqualTo(change)
  }

  @Test
  fun field_13__no_attribute_access_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .accessControl(AccessControl.Builder().attributes(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
      .build()
    val change = GroupChange.Actions.Builder()
      .modifyAttributesAccess(GroupChange.Actions.ModifyAttributesAccessControlAction.Builder().attributesAccess(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(GroupChangeUtil.changeIsEmpty(resolvedActions)).isTrue()
  }

  @Test
  fun field_14__membership_access_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .accessControl(AccessControl.Builder().members(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newMemberAccess(AccessControl.AccessRequired.MEMBER)
      .build()
    val change = GroupChange.Actions.Builder()
      .modifyMemberAccess(GroupChange.Actions.ModifyMembersAccessControlAction.Builder().membersAccess(AccessControl.AccessRequired.MEMBER).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(resolvedActions).isEqualTo(change)
  }

  @Test
  fun field_14__no_membership_access_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .accessControl(AccessControl.Builder().members(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
      .build()
    val change = GroupChange.Actions.Builder()
      .modifyMemberAccess(GroupChange.Actions.ModifyMembersAccessControlAction.Builder().membersAccess(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(GroupChangeUtil.changeIsEmpty(resolvedActions)).isTrue()
  }

  @Test
  fun field_15__no_membership_access_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .accessControl(AccessControl.Builder().addFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
      .build()
    val change = GroupChange.Actions.Builder()
      .modifyAddFromInviteLinkAccess(GroupChange.Actions.ModifyAddFromInviteLinkAccessControlAction.Builder().addFromInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(GroupChangeUtil.changeIsEmpty(resolvedActions)).isTrue()
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
      .newRequestingMembers(listOf(ProtoTestUtils.requestingMember(member1), ProtoTestUtils.requestingMember(member2), ProtoTestUtils.requestingMember(member3)))
      .build()

    val change = GroupChange.Actions.Builder()
      .addRequestingMembers(
        listOf(
          AddRequestingMemberAction.Builder().added(ProtoTestUtils.encryptedRequestingMember(member1, ProtoTestUtils.randomProfileKey())).build(),
          AddRequestingMemberAction.Builder().added(ProtoTestUtils.encryptedRequestingMember(member2, profileKey2)).build(),
          AddRequestingMemberAction.Builder().added(ProtoTestUtils.encryptedRequestingMember(member3, ProtoTestUtils.randomProfileKey())).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .addRequestingMembers(listOf(AddRequestingMemberAction.Builder().added(ProtoTestUtils.encryptedRequestingMember(member2, profileKey2)).build()))
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
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

    val change = GroupChange.Actions.Builder()
      .addRequestingMembers(
        listOf(
          AddRequestingMemberAction.Builder().added(ProtoTestUtils.encryptedRequestingMember(member1, profileKey1)).build(),
          AddRequestingMemberAction.Builder().added(ProtoTestUtils.encryptedRequestingMember(member2, profileKey2)).build(),
          AddRequestingMemberAction.Builder().added(ProtoTestUtils.encryptedRequestingMember(member3, profileKey3)).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .promotePendingMembers(
        listOf(
          PromotePendingMemberAction.Builder().presentation(ProtoTestUtils.presentation(member3, profileKey3)).build(),
          PromotePendingMemberAction.Builder().presentation(ProtoTestUtils.presentation(member1, profileKey1)).build()
        )
      )
      .addRequestingMembers(listOf(AddRequestingMemberAction.Builder().added(ProtoTestUtils.encryptedRequestingMember(member2, profileKey2)).build()))
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
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

    val change = GroupChange.Actions.Builder()
      .deleteRequestingMembers(
        listOf(
          DeleteRequestingMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member1)).build(),
          DeleteRequestingMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member2)).build(),
          DeleteRequestingMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member3)).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .deleteRequestingMembers(listOf(DeleteRequestingMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member2)).build()))
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
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

    val change = GroupChange.Actions.Builder()
      .promoteRequestingMembers(
        listOf(
          PromoteRequestingMemberAction.Builder().role(Member.Role.DEFAULT).userId(UuidUtil.toByteString(member1)).build(),
          PromoteRequestingMemberAction.Builder().role(Member.Role.DEFAULT).userId(UuidUtil.toByteString(member2)).build(),
          PromoteRequestingMemberAction.Builder().role(Member.Role.DEFAULT).userId(UuidUtil.toByteString(member3)).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .promoteRequestingMembers(listOf(PromoteRequestingMemberAction.Builder().role(Member.Role.DEFAULT).userId(UuidUtil.toByteString(member2)).build()))
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
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
    val change = GroupChange.Actions.Builder()
      .modifyInviteLinkPassword(GroupChange.Actions.ModifyInviteLinkPasswordAction.Builder().inviteLinkPassword(password2).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .modifyInviteLinkPassword(GroupChange.Actions.ModifyInviteLinkPasswordAction.Builder().inviteLinkPassword(password2).build())
      .build()
    assertThat(resolvedActions).isEqualTo(expected)
  }

  @Test
  fun field_20__description_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .description("Existing title")
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .newDescription(DecryptedString.Builder().value_("New title").build())
      .build()

    val change = GroupChange.Actions.Builder()
      .modifyDescription(GroupChange.Actions.ModifyDescriptionAction.Builder().description(ByteString.of(*"New title encrypted".toByteArray())).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(resolvedActions).isEqualTo(change)
  }

  @Test
  fun field_20__no_description_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .description("Existing title")
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newDescription(DecryptedString.Builder().value_("Existing title").build())
      .build()
    val change = GroupChange.Actions.Builder()
      .modifyDescription(GroupChange.Actions.ModifyDescriptionAction.Builder().description(ByteString.of(*"Existing title encrypted".toByteArray())).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(GroupChangeUtil.changeIsEmpty(resolvedActions)).isTrue()
  }

  @Test
  fun field_21__announcement_change_is_preserved() {
    val groupState = DecryptedGroup.Builder()
      .isAnnouncementGroup(EnabledState.DISABLED)
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newIsAnnouncementGroup(EnabledState.ENABLED)
      .build()
    val change = GroupChange.Actions.Builder()
      .modifyAnnouncementsOnly(GroupChange.Actions.ModifyAnnouncementsOnlyAction.Builder().announcementsOnly(true).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(resolvedActions).isEqualTo(change)
  }

  @Test
  fun field_21__announcement_change_is_removed() {
    val groupState = DecryptedGroup.Builder()
      .isAnnouncementGroup(EnabledState.ENABLED)
      .build()
    val decryptedChange = DecryptedGroupChange.Builder()
      .newIsAnnouncementGroup(EnabledState.ENABLED)
      .build()
    val change = GroupChange.Actions.Builder()
      .modifyAnnouncementsOnly(GroupChange.Actions.ModifyAnnouncementsOnlyAction.Builder().announcementsOnly(true).build())
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    assertThat(GroupChangeUtil.changeIsEmpty(resolvedActions)).isTrue()
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

    val change = GroupChange.Actions.Builder()
      .addBannedMembers(
        listOf(
          AddBannedMemberAction.Builder().added(BannedMember.Builder().userId(ProtoTestUtils.encrypt(member1)).build()).build(),
          AddBannedMemberAction.Builder().added(BannedMember.Builder().userId(ProtoTestUtils.encrypt(member2)).build()).build(),
          AddBannedMemberAction.Builder().added(BannedMember.Builder().userId(ProtoTestUtils.encrypt(member3)).build()).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .addBannedMembers(
        listOf(
          AddBannedMemberAction.Builder().added(BannedMember.Builder().userId(ProtoTestUtils.encrypt(member1)).build()).build(),
          AddBannedMemberAction.Builder().added(BannedMember.Builder().userId(ProtoTestUtils.encrypt(member2)).build()).build()
        )
      )
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
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

    val change = GroupChange.Actions.Builder()
      .deleteBannedMembers(
        listOf(
          DeleteBannedMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member1)).build(),
          DeleteBannedMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member2)).build(),
          DeleteBannedMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member3)).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .deleteBannedMembers(listOf(DeleteBannedMemberAction.Builder().deletedUserId(ProtoTestUtils.encrypt(member2)).build()))
      .build()

    assertThat(resolvedActions).isEqualTo(expected)
  }

  @Test
  fun field_24__promote_pending_members() {
    val member1 = ProtoTestUtils.pendingPniAciMember(UUID.randomUUID(), UUID.randomUUID(), ProtoTestUtils.randomProfileKey())
    val member2 = ProtoTestUtils.pendingPniAciMember(UUID.randomUUID(), UUID.randomUUID(), ProtoTestUtils.randomProfileKey())

    val groupState = DecryptedGroup.Builder()
      .members(listOf(ProtoTestUtils.member(UuidUtil.fromByteString(member1.aciBytes))))
      .build()

    val decryptedChange = DecryptedGroupChange.Builder()
      .promotePendingPniAciMembers(
        listOf(
          ProtoTestUtils.pendingPniAciMember(member1.aciBytes, member1.pniBytes, member1.profileKey),
          ProtoTestUtils.pendingPniAciMember(member2.aciBytes, member2.pniBytes, member2.profileKey)
        )
      )
      .build()

    val change = GroupChange.Actions.Builder()
      .promotePendingPniAciMembers(
        listOf(
          PromotePendingPniAciMemberProfileKeyAction.Builder().presentation(ProtoTestUtils.presentation(member1.pniBytes, member1.profileKey)).build(),
          PromotePendingPniAciMemberProfileKeyAction.Builder().presentation(ProtoTestUtils.presentation(member2.pniBytes, member2.profileKey)).build()
        )
      )
      .build()

    val resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build()

    val expected = GroupChange.Actions.Builder()
      .promotePendingPniAciMembers(listOf(PromotePendingPniAciMemberProfileKeyAction.Builder().presentation(ProtoTestUtils.presentation(member2.pniBytes, member2.profileKey)).build()))
      .build()
    assertThat(resolvedActions).isEqualTo(expected)
  }
}
