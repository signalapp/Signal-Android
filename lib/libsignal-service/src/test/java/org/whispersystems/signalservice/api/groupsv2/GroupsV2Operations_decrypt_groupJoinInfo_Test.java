package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Before;
import org.junit.Test;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupJoinInfo;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class GroupsV2Operations_decrypt_groupJoinInfo_Test {

  private GroupsV2Operations.GroupOperations groupOperations;

  @Before
  public void setup() throws InvalidInputException {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS();

    TestZkGroupServer  server             = new TestZkGroupServer();
    ClientZkOperations clientZkOperations = new ClientZkOperations(server.getServerPublicParams());
    GroupSecretParams  groupSecretParams  = GroupSecretParams.deriveFromMasterKey(new GroupMasterKey(Util.getSecretBytes(32)));

    groupOperations   = new GroupsV2Operations(clientZkOperations, 1000).forGroup(groupSecretParams);
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
                 8, maxFieldFound);
  }
  
  @Test
  public void decrypt_title_field_2() {
    GroupJoinInfo groupJoinInfo = new GroupJoinInfo.Builder()
                                               .title(groupOperations.encryptTitle("Title!"))
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals("Title!", decryptedGroupJoinInfo.title);
  }

  @Test
  public void avatar_field_passed_through_3() {
    GroupJoinInfo groupJoinInfo = new GroupJoinInfo.Builder()
                                               .avatar("AvatarCdnKey")
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals("AvatarCdnKey", decryptedGroupJoinInfo.avatar);
  }

  @Test
  public void member_count_passed_through_4() {
    GroupJoinInfo groupJoinInfo = new GroupJoinInfo.Builder()
                                               .memberCount(97)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals(97, decryptedGroupJoinInfo.memberCount);
  }

  @Test
  public void add_from_invite_link_access_control_passed_though_5_administrator() {
    GroupJoinInfo groupJoinInfo = new GroupJoinInfo.Builder()
                                               .addFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals(AccessControl.AccessRequired.ADMINISTRATOR, decryptedGroupJoinInfo.addFromInviteLink);
  }

  @Test
  public void add_from_invite_link_access_control_passed_though_5_any() {
    GroupJoinInfo groupJoinInfo = new GroupJoinInfo.Builder()
                                               .addFromInviteLink(AccessControl.AccessRequired.ANY)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals(AccessControl.AccessRequired.ANY, decryptedGroupJoinInfo.addFromInviteLink);
  }

  @Test
  public void revision_passed_though_6() {
    GroupJoinInfo groupJoinInfo = new GroupJoinInfo.Builder()
                                               .revision(11)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals(11, decryptedGroupJoinInfo.revision);
  }

  @Test
  public void pending_approval_passed_though_7_true() {
    GroupJoinInfo groupJoinInfo = new GroupJoinInfo.Builder()
                                               .pendingAdminApproval(true)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertTrue(decryptedGroupJoinInfo.pendingAdminApproval);
  }

  @Test
  public void pending_approval_passed_though_7_false() {
    GroupJoinInfo groupJoinInfo = new GroupJoinInfo.Builder()
                                               .pendingAdminApproval(false)
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertFalse(decryptedGroupJoinInfo.pendingAdminApproval);
  }

  @Test
  public void decrypt_description_field_8() {
    GroupJoinInfo groupJoinInfo = new GroupJoinInfo.Builder()
                                               .description(groupOperations.encryptDescription("Description!"))
                                               .build();

    DecryptedGroupJoinInfo decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo);

    assertEquals("Description!", decryptedGroupJoinInfo.description);
  }
}
