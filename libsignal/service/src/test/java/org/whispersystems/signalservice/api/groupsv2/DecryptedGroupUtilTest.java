package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.util.UUIDUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

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
  public void can_extract_editor_uuid_from_decrypted_group_change() {
    UUID                 uuid        = UUID.randomUUID();
    ByteString           editor      = UuidUtil.toByteString(uuid);
    DecryptedGroupChange groupChange = DecryptedGroupChange.newBuilder()
                                                           .setEditor(editor)
                                                           .build();

    UUID parsed = DecryptedGroupUtil.editorUuid(groupChange);

    assertEquals(uuid, parsed);
  }

}
