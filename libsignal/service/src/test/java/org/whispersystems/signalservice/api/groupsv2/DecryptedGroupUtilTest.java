package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.zkgroup.util.UUIDUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

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
    UUID                   uuid            = UUID.randomUUID();
    DecryptedPendingMember decryptedMember = DecryptedPendingMember.newBuilder()
                                                                   .setUuid(UuidUtil.toByteString(uuid))
                                                                   .build();

    UUID parsed = DecryptedGroupUtil.toUuid(decryptedMember);

    assertEquals(uuid, parsed);
  }

  @Test
  public void can_extract_uuid_from_bad_decrypted_pending_member() {
    DecryptedPendingMember decryptedMember = DecryptedPendingMember.newBuilder()
                                                                   .setUuid(ByteString.copyFrom(Util.getSecretBytes(17)))
                                                                   .build();

    UUID parsed = DecryptedGroupUtil.toUuid(decryptedMember);

    assertEquals(UuidUtil.UNKNOWN_UUID, parsed);
  }

  @Test
  public void can_extract_uuids_for_all_pending_including_bad_entries() {
    UUID                   uuid1            = UUID.randomUUID();
    UUID                   uuid2            = UUID.randomUUID();
    DecryptedPendingMember decryptedMember1 = DecryptedPendingMember.newBuilder()
                                                                    .setUuid(UuidUtil.toByteString(uuid1))
                                                                    .build();
    DecryptedPendingMember decryptedMember2 = DecryptedPendingMember.newBuilder()
                                                                    .setUuid(UuidUtil.toByteString(uuid2))
                                                                    .build();
    DecryptedPendingMember decryptedMember3 = DecryptedPendingMember.newBuilder()
                                                                    .setUuid(ByteString.copyFrom(Util.getSecretBytes(17)))
                                                                    .build();

    DecryptedGroupChange groupChange = DecryptedGroupChange.newBuilder()
                                                           .addNewPendingMembers(decryptedMember1)
                                                           .addNewPendingMembers(decryptedMember2)
                                                           .addNewPendingMembers(decryptedMember3)
                                                           .build();

    List<UUID> pendingUuids = DecryptedGroupUtil.pendingToUuidList(groupChange.getNewPendingMembersList());

    assertThat(pendingUuids, is(asList(uuid1, uuid2, UuidUtil.UNKNOWN_UUID)));
  }

  @Test
  public void can_extract_uuids_for_all_deleted_pending_excluding_bad_entries() {
    UUID                          uuid1            = UUID.randomUUID();
    UUID                          uuid2            = UUID.randomUUID();
    DecryptedPendingMemberRemoval decryptedMember1 = DecryptedPendingMemberRemoval.newBuilder()
                                                                    .setUuid(UuidUtil.toByteString(uuid1))
                                                                    .build();
    DecryptedPendingMemberRemoval decryptedMember2 = DecryptedPendingMemberRemoval.newBuilder()
                                                                    .setUuid(UuidUtil.toByteString(uuid2))
                                                                    .build();
    DecryptedPendingMemberRemoval decryptedMember3 = DecryptedPendingMemberRemoval.newBuilder()
                                                                    .setUuid(ByteString.copyFrom(Util.getSecretBytes(17)))
                                                                    .build();

    DecryptedGroupChange groupChange = DecryptedGroupChange.newBuilder()
                                                           .addDeletePendingMembers(decryptedMember1)
                                                           .addDeletePendingMembers(decryptedMember2)
                                                           .addDeletePendingMembers(decryptedMember3)
                                                           .build();

    List<UUID> removedUuids = DecryptedGroupUtil.removedPendingMembersUuidList(groupChange);

    assertThat(removedUuids, is(asList(uuid1, uuid2)));
  }

  @Test
  public void can_extract_uuids_for_all_deleted_members_excluding_bad_entries() {
    UUID                 uuid1       = UUID.randomUUID();
    UUID                 uuid2       = UUID.randomUUID();
    DecryptedGroupChange groupChange = DecryptedGroupChange.newBuilder()
                                                           .addDeleteMembers(UuidUtil.toByteString(uuid1))
                                                           .addDeleteMembers(UuidUtil.toByteString(uuid2))
                                                           .addDeleteMembers(ByteString.copyFrom(Util.getSecretBytes(17)))
                                                           .build();

    List<UUID> removedUuids = DecryptedGroupUtil.removedMembersUuidList(groupChange);

    assertThat(removedUuids, is(asList(uuid1, uuid2)));
  }
}
