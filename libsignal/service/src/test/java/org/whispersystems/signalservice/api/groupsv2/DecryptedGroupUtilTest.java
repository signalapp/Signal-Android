package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Test;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.List;
import java.util.UUID;

import okio.ByteString;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static java.util.Arrays.asList;

public final class DecryptedGroupUtilTest {

  @Test
  public void can_extract_editor_uuid_from_decrypted_group_change() {
    ACI        aci                   = ACI.from(UUID.randomUUID());
    ByteString editor                = aci.toByteString();
    DecryptedGroupChange groupChange = new DecryptedGroupChange.Builder()
                                                           .editorServiceIdBytes(editor)
                                                           .build();

    ServiceId parsed = DecryptedGroupUtil.editorServiceId(groupChange).get();

    assertEquals(aci, parsed);
  }

  @Test
  public void can_extract_uuid_from_decrypted_pending_member() {
    ACI                    aci             = ACI.from(UUID.randomUUID());
    DecryptedPendingMember decryptedMember = new DecryptedPendingMember.Builder()
                                                                   .serviceIdBytes(aci.toByteString())
                                                                   .build();

    ServiceId parsed = ServiceId.parseOrNull(decryptedMember.serviceIdBytes);

    assertEquals(aci, parsed);
  }

  @Test
  public void can_extract_uuid_from_bad_decrypted_pending_member() {
    DecryptedPendingMember decryptedMember = new DecryptedPendingMember.Builder()
                                                                   .serviceIdBytes(ByteString.of(Util.getSecretBytes(18)))
                                                                   .build();

    ServiceId parsed = ServiceId.parseOrNull(decryptedMember.serviceIdBytes);

    assertNull(parsed);
  }

  @Test
  public void can_extract_uuids_for_all_pending_including_bad_entries() {
    ACI                    aci1             = ACI.from(UUID.randomUUID());
    ACI                    aci2             = ACI.from(UUID.randomUUID());
    DecryptedPendingMember decryptedMember1 = new DecryptedPendingMember.Builder()
                                                                    .serviceIdBytes(aci1.toByteString())
                                                                    .build();
    DecryptedPendingMember decryptedMember2 = new DecryptedPendingMember.Builder()
                                                                    .serviceIdBytes(aci2.toByteString())
                                                                    .build();
    DecryptedPendingMember decryptedMember3 = new DecryptedPendingMember.Builder()
                                                                    .serviceIdBytes(ByteString.of(Util.getSecretBytes(18)))
                                                                    .build();

    DecryptedGroupChange groupChange = new DecryptedGroupChange.Builder()
                                                               .newPendingMembers(asList(decryptedMember1, decryptedMember2, decryptedMember3))
                                                               .build();

    List<ServiceId> pendingUuids = DecryptedGroupUtil.pendingToServiceIdList(groupChange.newPendingMembers);

    assertThat(pendingUuids, is(asList(aci1, aci2, ACI.UNKNOWN)));
  }

  @Test
  public void can_extract_uuids_for_all_deleted_pending_excluding_bad_entries() {
    ACI                           aci1             = ACI.from(UUID.randomUUID());
    ACI                           aci2             = ACI.from(UUID.randomUUID());
    DecryptedPendingMemberRemoval decryptedMember1 = new DecryptedPendingMemberRemoval.Builder()
                                                                    .serviceIdBytes(aci1.toByteString())
                                                                    .build();
    DecryptedPendingMemberRemoval decryptedMember2 = new DecryptedPendingMemberRemoval.Builder()
                                                                    .serviceIdBytes(aci2.toByteString())
                                                                    .build();
    DecryptedPendingMemberRemoval decryptedMember3 = new DecryptedPendingMemberRemoval.Builder()
                                                                    .serviceIdBytes(ByteString.of(Util.getSecretBytes(18)))
                                                                    .build();

    DecryptedGroupChange groupChange = new DecryptedGroupChange.Builder()
                                                               .deletePendingMembers(asList(decryptedMember1, decryptedMember2, decryptedMember3))
                                                               .build();

    List<ServiceId> removedUuids = DecryptedGroupUtil.removedPendingMembersServiceIdList(groupChange);

    assertThat(removedUuids, is(asList(aci1, aci2)));
  }

  @Test
  public void can_extract_uuids_for_all_deleted_members_excluding_bad_entries() {
    ACI                  aci1        = ACI.from(UUID.randomUUID());
    ACI                  aci2        = ACI.from(UUID.randomUUID());
    DecryptedGroupChange groupChange = new DecryptedGroupChange.Builder()
                                                               .deleteMembers(asList(aci1.toByteString(), aci2.toByteString(), ByteString.of(Util.getSecretBytes(18))))
                                                               .build();

    List<ServiceId> removedServiceIds = DecryptedGroupUtil.removedMembersServiceIdList(groupChange);

    assertThat(removedServiceIds, is(asList(aci1, aci2)));
  }
}
