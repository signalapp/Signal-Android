package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Test;
import org.signal.core.util.UuidUtil;
import org.signal.storageservice.storage.protos.groups.AccessControl;
import org.signal.storageservice.storage.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.storage.protos.groups.local.DecryptedBannedMember;
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.storage.protos.groups.local.DecryptedModifyMemberLabel;
import org.signal.storageservice.storage.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.storage.protos.groups.local.DecryptedString;
import org.signal.storageservice.storage.protos.groups.local.DecryptedTimer;
import org.signal.storageservice.storage.protos.groups.local.EnabledState;

import java.util.List;
import java.util.UUID;

import okio.ByteString;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.member;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMemberRemoval;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingPniAciMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.promoteAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.randomProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

@SuppressWarnings("NewClassNamingConvention")
public final class DecryptedGroupUtil_empty_Test {
  /**
   * Ensures that {@link GroupChangeField} enum covers all fields of {@link DecryptedGroupChange}.
   * <p>
   * If this test fails after a proto update, add the new field to {@link GroupChangeField}
   * and update {{@link DecryptedGroupExtensions#getChangedFields(DecryptedGroupChange)}}.
   */
  @Test
  public void ensure_GroupChangeField_knows_about_all_fields_of_DecryptedGroupChange() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroupChange.class);

    assertEquals("GroupChangeField and getChangedFields() need updating to account for new fields on " + DecryptedGroupChange.class.getName(),
                 26, maxFieldFound);
  }

  @Test
  public void empty_change_set() {
    assertTrue(DecryptedGroupExtensions.getChangedFields(new DecryptedGroupChange.Builder().build()).isEmpty());
  }

  @Test
  public void not_empty_with_add_member_field_3() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newMembers(List.of(member(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_delete_member_field_4() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .deleteMembers(List.of(UuidUtil.toByteString(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_modify_member_roles_field_5() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .modifyMemberRoles(List.of(promoteAdmin(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_modify_profile_keys_field_6() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .modifiedProfileKeys(List.of(member(UUID.randomUUID(), randomProfileKey())))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertTrue(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_add_pending_members_field_7() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newPendingMembers(List.of(pendingMember(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_delete_pending_members_field_8() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .deletePendingMembers(List.of(pendingMemberRemoval(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_promote_delete_pending_members_field_9() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .promotePendingMembers(List.of(member(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_modify_title_field_10() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newTitle(new DecryptedString.Builder().value_("New title").build())
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_modify_avatar_field_11() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newAvatar(new DecryptedString.Builder().value_("New Avatar").build())
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_modify_disappearing_message_timer_field_12() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newTimer(new DecryptedTimer.Builder().duration(60).build())
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_modify_attributes_field_13() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_modify_member_access_field_14() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newMemberAccess(AccessControl.AccessRequired.MEMBER)
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_modify_add_from_invite_link_access_field_15() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_an_add_requesting_member_field_16() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newRequestingMembers(List.of(new DecryptedRequestingMember()))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_a_delete_requesting_member_field_17() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .deleteRequestingMembers(List.of(ByteString.of(new byte[16])))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_a_promote_requesting_member_field_18() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .promoteRequestingMembers(List.of(new DecryptedApproveMember()))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_a_new_invite_link_password_19() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newInviteLinkPassword(ByteString.of(new byte[16]))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_modify_description_field_20() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newDescription(new DecryptedString.Builder().value_("New description").build())
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_modify_announcement_field_21() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newIsAnnouncementGroup(EnabledState.ENABLED)
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_add_banned_member_field_22() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newBannedMembers(List.of(new DecryptedBannedMember()))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertTrue(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_delete_banned_member_field_23() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .deleteBannedMembers(List.of(new DecryptedBannedMember()))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_empty_with_promote_pending_pni_aci_members_field_24() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .promotePendingPniAciMembers(List.of(pendingPniAciMember(UUID.randomUUID(), UUID.randomUUID(), randomProfileKey())))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void silent_with_modify_member_label_field_26() {
    DecryptedModifyMemberLabel modifyLabelAction = new DecryptedModifyMemberLabel.Builder()
        .aciBytes(UuidUtil.toByteString(UUID.randomUUID()))
        .labelEmoji("🔥")
        .labelString("Test")
        .build();

    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .modifyMemberLabels(List.of(modifyLabelAction))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertTrue(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void silent_with_profile_keys_and_banned_members() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .modifiedProfileKeys(List.of(member(UUID.randomUUID(), randomProfileKey())))
        .newBannedMembers(List.of(new DecryptedBannedMember()))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertTrue(DecryptedGroupExtensions.isSilent(change));
  }

  @Test
  public void not_silent_with_profile_keys_and_new_members() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .modifiedProfileKeys(List.of(member(UUID.randomUUID(), randomProfileKey())))
        .newMembers(List.of(member(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupExtensions.getChangedFields(change).isEmpty());
    assertFalse(DecryptedGroupExtensions.isSilent(change));
  }
}
