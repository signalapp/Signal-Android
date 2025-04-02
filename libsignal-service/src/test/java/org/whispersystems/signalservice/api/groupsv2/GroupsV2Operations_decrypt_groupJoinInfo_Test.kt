package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.GroupJoinInfo
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations.GroupOperations
import org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber
import org.whispersystems.signalservice.internal.util.Util
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil

class GroupsV2Operations_decrypt_groupJoinInfo_Test {
  private lateinit var groupOperations: GroupOperations

  @Before
  fun setup() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    val server = TestZkGroupServer()
    val clientZkOperations = ClientZkOperations(server.serverPublicParams)
    val groupSecretParams = GroupSecretParams.deriveFromMasterKey(GroupMasterKey(Util.getSecretBytes(32)))

    groupOperations = GroupsV2Operations(clientZkOperations, 1000).forGroup(groupSecretParams)
  }

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * If we didn't, newly added fields would not be decrypted by [GroupsV2Operations.GroupOperations.decryptGroupJoinInfo].
   */
  @Test
  fun ensure_GroupOperations_knows_about_all_fields_of_Group() {
    assertThat(
      actual = getMaxDeclaredFieldNumber(GroupJoinInfo::class.java),
      name = "GroupOperations and its tests need updating to account for new fields on " + GroupJoinInfo::class.java.name
    ).isEqualTo(8)
  }

  @Test
  fun decrypt_title_field_2() {
    val groupJoinInfo = GroupJoinInfo.Builder()
      .title(groupOperations.encryptTitle("Title!"))
      .build()

    val decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo)

    assertThat(decryptedGroupJoinInfo.title).isEqualTo("Title!")
  }

  @Test
  fun avatar_field_passed_through_3() {
    val groupJoinInfo = GroupJoinInfo.Builder()
      .avatar("AvatarCdnKey")
      .build()

    val decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo)

    assertThat(decryptedGroupJoinInfo.avatar).isEqualTo("AvatarCdnKey")
  }

  @Test
  fun member_count_passed_through_4() {
    val groupJoinInfo = GroupJoinInfo.Builder()
      .memberCount(97)
      .build()

    val decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo)

    assertThat(decryptedGroupJoinInfo.memberCount.toLong()).isEqualTo(97)
  }

  @Test
  fun add_from_invite_link_access_control_passed_though_5_administrator() {
    val groupJoinInfo = GroupJoinInfo.Builder()
      .addFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR)
      .build()

    val decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo)

    assertThat(decryptedGroupJoinInfo.addFromInviteLink).isEqualTo(AccessControl.AccessRequired.ADMINISTRATOR)
  }

  @Test
  fun add_from_invite_link_access_control_passed_though_5_any() {
    val groupJoinInfo = GroupJoinInfo.Builder()
      .addFromInviteLink(AccessControl.AccessRequired.ANY)
      .build()

    val decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo)

    assertThat(decryptedGroupJoinInfo.addFromInviteLink).isEqualTo(AccessControl.AccessRequired.ANY)
  }

  @Test
  fun revision_passed_though_6() {
    val groupJoinInfo = GroupJoinInfo.Builder()
      .revision(11)
      .build()

    val decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo)

    assertThat(decryptedGroupJoinInfo.revision.toLong()).isEqualTo(11)
  }

  @Test
  fun pending_approval_passed_though_7_true() {
    val groupJoinInfo = GroupJoinInfo.Builder()
      .pendingAdminApproval(true)
      .build()

    val decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo)

    assertThat(decryptedGroupJoinInfo.pendingAdminApproval).isTrue()
  }

  @Test
  fun pending_approval_passed_though_7_false() {
    val groupJoinInfo = GroupJoinInfo.Builder()
      .pendingAdminApproval(false)
      .build()

    val decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo)

    assertThat(decryptedGroupJoinInfo.pendingAdminApproval).isFalse()
  }

  @Test
  fun decrypt_description_field_8() {
    val groupJoinInfo = GroupJoinInfo.Builder()
      .description(groupOperations.encryptDescription("Description!"))
      .build()

    val decryptedGroupJoinInfo = groupOperations.decryptGroupJoinInfo(groupJoinInfo)

    assertThat(decryptedGroupJoinInfo.description).isEqualTo("Description!")
  }
}
