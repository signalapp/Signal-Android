package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static java.util.Arrays.asList;

public final class DecryptedGroupUtilTest {

  @Test
  public void can_extract_uuid_from_decrypted_member() {
    UUID            uuid            = UUID.randomUUID();
    DecryptedMember decryptedMember = DecryptedMember.newBuilder()
                                                     .setUuid(UuidUtil.toByteString(uuid))
                                                     .build();

    UUID parsed = DecryptedGroupUtil.toUuid(decryptedMember);

    assertEquals(uuid, parsed);
  }

  @Test
  public void can_extract_uuid_from_bad_decrypted_member() {
    DecryptedMember decryptedMember = DecryptedMember.newBuilder()
                                                     .setUuid(ByteString.copyFrom(new byte[0]))
                                                     .build();

    UUID parsed = DecryptedGroupUtil.toUuid(decryptedMember);

    assertEquals(UuidUtil.UNKNOWN_UUID, parsed);
  }

  @Test
  public void can_extract_editor_uuid_from_decrypted_group_change() {
    UUID                 uuid        = UUID.randomUUID();
    ByteString           editor      = UuidUtil.toByteString(uuid);
    DecryptedGroupChange groupChange = DecryptedGroupChange.newBuilder()
                                                           .setEditor(editor)
                                                           .build();

    UUID parsed = DecryptedGroupUtil.editorUuid(groupChange).get();

    assertEquals(uuid, parsed);
  }

  @Test
  public void can_extract_uuid_from_decrypted_pending_member() {
    ACI                    aci             = ACI.from(UUID.randomUUID());
    DecryptedPendingMember decryptedMember = DecryptedPendingMember.newBuilder()
                                                                   .setServiceIdBinary(aci.toByteString())
                                                                   .build();

    ServiceId parsed = ServiceId.parseOrNull(decryptedMember.getServiceIdBinary());

    assertEquals(aci, parsed);
  }

  @Test
  public void can_extract_uuid_from_bad_decrypted_pending_member() {
    DecryptedPendingMember decryptedMember = DecryptedPendingMember.newBuilder()
                                                                   .setServiceIdBinary(ByteString.copyFrom(Util.getSecretBytes(18)))
                                                                   .build();

    ServiceId parsed = ServiceId.parseOrNull(decryptedMember.getServiceIdBinary());

    assertNull(parsed);
  }

  @Test
  public void can_extract_uuids_for_all_pending_including_bad_entries() {
    ACI                    aci1             = ACI.from(UUID.randomUUID());
    ACI                    aci2             = ACI.from(UUID.randomUUID());
    DecryptedPendingMember decryptedMember1 = DecryptedPendingMember.newBuilder()
                                                                    .setServiceIdBinary(aci1.toByteString())
                                                                    .build();
    DecryptedPendingMember decryptedMember2 = DecryptedPendingMember.newBuilder()
                                                                    .setServiceIdBinary(aci2.toByteString())
                                                                    .build();
    DecryptedPendingMember decryptedMember3 = DecryptedPendingMember.newBuilder()
                                                                    .setServiceIdBinary(ByteString.copyFrom(Util.getSecretBytes(18)))
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
                                                                    .setServiceIdBinary(aci1.toByteString())
                                                                    .build();
    DecryptedPendingMemberRemoval decryptedMember2 = DecryptedPendingMemberRemoval.newBuilder()
                                                                    .setServiceIdBinary(aci2.toByteString())
                                                                    .build();
    DecryptedPendingMemberRemoval decryptedMember3 = DecryptedPendingMemberRemoval.newBuilder()
                                                                    .setServiceIdBinary(ByteString.copyFrom(Util.getSecretBytes(18)))
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
