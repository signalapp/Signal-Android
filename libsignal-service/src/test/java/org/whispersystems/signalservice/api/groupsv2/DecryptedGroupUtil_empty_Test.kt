package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import okio.ByteString
import org.junit.Test
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.signal.storageservice.protos.groups.local.DecryptedString
import org.signal.storageservice.protos.groups.local.DecryptedTimer
import org.signal.storageservice.protos.groups.local.EnabledState
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID

class DecryptedGroupUtil_empty_Test {
  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   *
   *
   * If we didn't, newly added fields would easily affect [DecryptedGroupUtil]'s ability to detect non-empty change states.
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
  fun empty_change_set() {
    assertThat(DecryptedGroupUtil.changeIsEmpty(DecryptedGroupChange.Builder().build())).isTrue()
  }

  @Test
  fun not_empty_with_add_member_field_3() {
    val change = DecryptedGroupChange.Builder()
      .newMembers(listOf(ProtoTestUtils.member(UUID.randomUUID())))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_delete_member_field_4() {
    val change = DecryptedGroupChange.Builder()
      .deleteMembers(listOf(UuidUtil.toByteString(UUID.randomUUID())))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_modify_member_roles_field_5() {
    val change = DecryptedGroupChange.Builder()
      .modifyMemberRoles(listOf(ProtoTestUtils.promoteAdmin(UUID.randomUUID())))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_modify_profile_keys_field_6() {
    val change = DecryptedGroupChange.Builder()
      .modifiedProfileKeys(listOf(ProtoTestUtils.member(UUID.randomUUID(), ProtoTestUtils.randomProfileKey())))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isTrue()
  }

  @Test
  fun not_empty_with_add_pending_members_field_7() {
    val change = DecryptedGroupChange.Builder()
      .newPendingMembers(listOf(ProtoTestUtils.pendingMember(UUID.randomUUID())))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_delete_pending_members_field_8() {
    val change = DecryptedGroupChange.Builder()
      .deletePendingMembers(listOf(ProtoTestUtils.pendingMemberRemoval(UUID.randomUUID())))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_promote_delete_pending_members_field_9() {
    val change = DecryptedGroupChange.Builder()
      .promotePendingMembers(listOf(ProtoTestUtils.member(UUID.randomUUID())))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_modify_title_field_10() {
    val change = DecryptedGroupChange.Builder()
      .newTitle(DecryptedString.Builder().value_("New title").build())
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_modify_avatar_field_11() {
    val change = DecryptedGroupChange.Builder()
      .newAvatar(DecryptedString.Builder().value_("New Avatar").build())
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_modify_disappearing_message_timer_field_12() {
    val change = DecryptedGroupChange.Builder()
      .newTimer(DecryptedTimer.Builder().duration(60).build())
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_modify_attributes_field_13() {
    val change = DecryptedGroupChange.Builder()
      .newAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_modify_member_access_field_14() {
    val change = DecryptedGroupChange.Builder()
      .newMemberAccess(AccessControl.AccessRequired.MEMBER)
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_modify_add_from_invite_link_access_field_15() {
    val change = DecryptedGroupChange.Builder()
      .newInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_an_add_requesting_member_field_16() {
    val change = DecryptedGroupChange.Builder()
      .newRequestingMembers(listOf(DecryptedRequestingMember()))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_a_delete_requesting_member_field_17() {
    val change = DecryptedGroupChange.Builder()
      .deleteRequestingMembers(listOf(ByteString.of(*ByteArray(16))))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_a_promote_requesting_member_field_18() {
    val change = DecryptedGroupChange.Builder()
      .promoteRequestingMembers(listOf(DecryptedApproveMember()))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_a_new_invite_link_password_19() {
    val change = DecryptedGroupChange.Builder()
      .newInviteLinkPassword(ByteString.of(*ByteArray(16)))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_modify_description_field_20() {
    val change = DecryptedGroupChange.Builder()
      .newDescription(DecryptedString.Builder().value_("New description").build())
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_modify_announcement_field_21() {
    val change = DecryptedGroupChange.Builder()
      .newIsAnnouncementGroup(EnabledState.ENABLED)
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_add_banned_member_field_22() {
    val change = DecryptedGroupChange.Builder()
      .newBannedMembers(listOf(DecryptedBannedMember()))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_delete_banned_member_field_23() {
    val change = DecryptedGroupChange.Builder()
      .deleteBannedMembers(listOf(DecryptedBannedMember()))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }

  @Test
  fun not_empty_with_promote_pending_pni_aci_members_field_24() {
    val change = DecryptedGroupChange.Builder()
      .promotePendingPniAciMembers(listOf(ProtoTestUtils.pendingPniAciMember(UUID.randomUUID(), UUID.randomUUID(), ProtoTestUtils.randomProfileKey())))
      .build()

    assertThat(DecryptedGroupUtil.changeIsEmpty(change)).isFalse()
    assertThat(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change)).isFalse()
  }
}
