package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Before;
import org.junit.Test;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupJoinInfo;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.testutil.ZkGroupLibraryUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class GroupsV2Operations_decrypt_groupJoinInfo_Test {

  private GroupsV2Operations.GroupOperations groupOperations;

  @Before
  public void setup() throws InvalidInputException {
    ZkGroupLibraryUtil.assumeZkGroupSupportedOnOS();

    TestZkGroupServer  server             = new TestZkGroupServer();
    ClientZkOperations clientZkOperations = new ClientZkOperations(server.getServerPublicParams());
    GroupSecretParams  groupSecretParams  = GroupSecretParams.deriveFromMasterKey(new GroupMasterKey(Util.getSecretBytes(32)));

    groupOperations   = new GroupsV2Operations(clientZkOperations).forGroup(groupSecretParams);
  }

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would not be decrypted by {@link GroupsV2Operations.GroupOperations#decryptGroupJoinInfo}.
   */
  @Test
  public void ensure_GroupOperations_knows_about_all_fields_of_Group() {
    int maxFieldFound = getMaxDeclaredFieldNumber(GroupJoinInfo.class);

    assertEquals("GroupOperations and its tests need updating to account for new fields on " + GroupJoinInfo.class.getName(),
                 7, maxFieldFound);
  }
  
  @Test
  public void decrypt_title_field_2() {
    GroupJoinInfo groupJoinInfo = GroupJoinInfo.newBuilder()
                                               .setTitle(groupOperations.encryptTitle("Title!"))
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals("Title!", decryptedGroupJoinInfo.getTitle());
  }

  @Test
  public void avatar_field_passed_through_3() {
    GroupJoinInfo groupJoinInfo = GroupJoinInfo.newBuilder()
                                               .setAvatar("AvatarCdnKey")
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals("AvatarCdnKey", decryptedGroupJoinInfo.getAvatar());
  }

  @Test
  public void member_count_passed_through_4() {
    GroupJoinInfo groupJoinInfo = GroupJoinInfo.newBuilder()
                                               .setMemberCount(97)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals(97, decryptedGroupJoinInfo.getMemberCount());
  }

  @Test
  public void add_from_invite_link_access_control_passed_though_5_administrator() {
    GroupJoinInfo groupJoinInfo = GroupJoinInfo.newBuilder()
                                               .setAddFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals(AccessControl.AccessRequired.ADMINISTRATOR, decryptedGroupJoinInfo.getAddFromInviteLink());
  }

  @Test
  public void add_from_invite_link_access_control_passed_though_5_any() {
    GroupJoinInfo groupJoinInfo = GroupJoinInfo.newBuilder()
                                               .setAddFromInviteLink(AccessControl.AccessRequired.ANY)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals(AccessControl.AccessRequired.ANY, decryptedGroupJoinInfo.getAddFromInviteLink());
  }

  @Test
  public void revision_passed_though_6() {
    GroupJoinInfo groupJoinInfo = GroupJoinInfo.newBuilder()
                                               .setRevision(11)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals(11, decryptedGroupJoinInfo.getRevision());
  }

  @Test
  public void pending_approval_passed_though_7_true() {
    GroupJoinInfo groupJoinInfo = GroupJoinInfo.newBuilder()
                                               .setPendingAdminApproval(true)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertTrue(decryptedGroupJoinInfo.getPendingAdminApproval());
  }

  @Test
  public void pending_approval_passed_though_7_false() {
    GroupJoinInfo groupJoinInfo = GroupJoinInfo.newBuilder()
                                               .setPendingAdminApproval(false)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertFalse(decryptedGroupJoinInfo.getPendingAdminApproval());
  }
}
