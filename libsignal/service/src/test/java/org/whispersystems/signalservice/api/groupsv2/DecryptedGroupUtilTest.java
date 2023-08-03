package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static java.util.Arrays.asList;

public final class DecryptedGroupUtilTest {

  @Test
  public void can_extract_editor_uuid_from_decrypted_group_change() {
    ACI                  aci         = ACI.from(UUID.randomUUID());
    ByteString           editor      = aci.toByteString();
    DecryptedGroupChange groupChange = DecryptedGroupChange.newBuilder()
                                                           .setEditorServiceIdBytes(editor)
                                                           .build();

    ServiceId parsed = DecryptedGroupUtil.editorServiceId(groupChange).get();

    assertEquals(aci, parsed);
  }

  @Test
  public void can_extract_uuid_from_decrypted_pending_member() {
    ACI                    aci             = ACI.from(UUID.randomUUID());
    DecryptedPendingMember decryptedMember = DecryptedPendingMember.newBuilder()
                                                                   .setServiceIdBytes(aci.toByteString())
                                                                   .build();

    ServiceId parsed = ServiceId.parseOrNull(decryptedMember.getServiceIdBytes());

    assertEquals(aci, parsed);
  }

  @Test
  public void can_extract_uuid_from_bad_decrypted_pending_member() {
    DecryptedPendingMember decryptedMember = DecryptedPendingMember.newBuilder()
                                                                   .setServiceIdBytes(ByteString.copyFrom(Util.getSecretBytes(18)))
                                                                   .build();

    ServiceId parsed = ServiceId.parseOrNull(decryptedMember.getServiceIdBytes());

    assertNull(parsed);
  }

  @Test
  public void can_extract_uuids_for_all_pending_including_bad_entries() {
    ACI                    aci1             = ACI.from(UUID.randomUUID());
    ACI                    aci2             = ACI.from(UUID.randomUUID());
    DecryptedPendingMember decryptedMember1 = DecryptedPendingMember.newBuilder()
                                                                    .setServiceIdBytes(aci1.toByteString())
                                                                    .build();
    DecryptedPendingMember decryptedMember2 = DecryptedPendingMember.newBuilder()
                                                                    .setServiceIdBytes(aci2.toByteString())
                                                                    .build();
    DecryptedPendingMember decryptedMember3 = DecryptedPendingMember.newBuilder()
                                                                    .setServiceIdBytes(ByteString.copyFrom(Util.getSecretBytes(18)))
                                                                    .build();

    DecryptedGroupChange groupChange = DecryptedGroupChange.newBuilder()
                                                           .addNewPendingMembers(decryptedMember1)
                                                           .addNewPendingMembers(decryptedMember2)
                                                           .addNewPendingMembers(decryptedMember3)
                                                           .build();

    List<ServiceId> pendingUuids = DecryptedGroupUtil.pendingToServiceIdList(groupChange.getNewPendingMembersList());

    assertThat(pendingUuids, is(asList(aci1, aci2, ACI.UNKNOWN)));
  }

  @Test
  public void can_extract_uuids_for_all_deleted_pending_excluding_bad_entries() {
    ACI                           aci1             = ACI.from(UUID.randomUUID());
    ACI                           aci2             = ACI.from(UUID.randomUUID());
    DecryptedPendingMemberRemoval decryptedMember1 = DecryptedPendingMemberRemoval.newBuilder()
                                                                    .setServiceIdBytes(aci1.toByteString())
                                                                    .build();
    DecryptedPendingMemberRemoval decryptedMember2 = DecryptedPendingMemberRemoval.newBuilder()
                                                                    .setServiceIdBytes(aci2.toByteString())
                                                                    .build();
    DecryptedPendingMemberRemoval decryptedMember3 = DecryptedPendingMemberRemoval.newBuilder()
                                                                    .setServiceIdBytes(ByteString.copyFrom(Util.getSecretBytes(18)))
                                                                    .build();

    DecryptedGroupChange groupChange = DecryptedGroupChange.newBuilder()
                                                           .addDeletePendingMembers(decryptedMember1)
                                                           .addDeletePendingMembers(decryptedMember2)
                                                           .addDeletePendingMembers(decryptedMember3)
                                                           .build();

    List<ServiceId> removedUuids = DecryptedGroupUtil.removedPendingMembersServiceIdList(groupChange);

    assertThat(removedUuids, is(asList(aci1, aci2)));
  }

  @Test
  public void can_extract_uuids_for_all_deleted_members_excluding_bad_entries() {
    ACI                  aci1        = ACI.from(UUID.randomUUID());
    ACI                  aci2        = ACI.from(UUID.randomUUID());
    DecryptedGroupChange groupChange = DecryptedGroupChange.newBuilder()
                                                           .addDeleteMembers(aci1.toByteString())
                                                           .addDeleteMembers(aci2.toByteString())
                                                           .addDeleteMembers(ByteString.copyFrom(Util.getSecretBytes(18)))
                                                           .build();

    List<ServiceId> removedServiceIds = DecryptedGroupUtil.removedMembersServiceIdList(groupChange);

    assertThat(removedServiceIds, is(asList(aci1, aci2)));
  }
}
