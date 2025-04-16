package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.ByteString
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.groups.ClientZkGroupCipher
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.BannedMember
import org.signal.storageservice.protos.groups.Group
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.PendingMember
import org.signal.storageservice.protos.groups.RequestingMember
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.signal.storageservice.protos.groups.local.EnabledState
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations.GroupOperations
import org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.ACI.Companion.from
import org.whispersystems.signalservice.internal.util.Util
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil
import java.util.UUID

class GroupsV2Operations_decrypt_group_Test {
  private lateinit var groupSecretParams: GroupSecretParams
  private lateinit var groupOperations: GroupOperations

  @Before
  fun setup() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    val server = TestZkGroupServer()
    val clientZkOperations = ClientZkOperations(server.serverPublicParams)

    groupSecretParams = GroupSecretParams.deriveFromMasterKey(GroupMasterKey(Util.getSecretBytes(32)))
    groupOperations = GroupsV2Operations(clientZkOperations, 1000).forGroup(groupSecretParams)
  }

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   *
   *
   * If we didn't, newly added fields would not be decrypted by [GroupsV2Operations.GroupOperations.decryptGroup].
   */
  @Test
  fun ensure_GroupOperations_knows_about_all_fields_of_Group() {
    assertThat(
      actual = getMaxDeclaredFieldNumber(Group::class.java),
      name = "GroupOperations and its tests need updating to account for new fields on " + Group::class.java.name
    ).isEqualTo(13)
  }

  @Test
  fun decrypt_title_field_2() {
    val group = Group.Builder()
      .title(groupOperations.encryptTitle("Title!"))
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.title).isEqualTo("Title!")
  }

  @Test
  fun avatar_field_passed_through_3() {
    val group = Group.Builder()
      .avatar("AvatarCdnKey")
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.avatar).isEqualTo("AvatarCdnKey")
  }

  @Test
  fun decrypt_message_timer_field_4() {
    val group = Group.Builder()
      .disappearingMessagesTimer(groupOperations.encryptTimer(123))
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.disappearingMessagesTimer!!.duration.toLong()).isEqualTo(123)
  }

  @Test
  fun pass_through_access_control_field_5() {
    val accessControl = AccessControl.Builder()
      .members(AccessControl.AccessRequired.ADMINISTRATOR)
      .attributes(AccessControl.AccessRequired.MEMBER)
      .addFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE)
      .build()
    val group = Group.Builder()
      .accessControl(accessControl)
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.accessControl).isEqualTo(accessControl)
  }

  @Test
  fun set_revision_field_6() {
    val group = Group.Builder()
      .revision(99)
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.revision.toLong()).isEqualTo(99)
  }

  @Test
  fun decrypt_full_members_field_7() {
    val admin1 = from(UUID.randomUUID())
    val member1 = from(UUID.randomUUID())
    val adminProfileKey = newProfileKey()
    val memberProfileKey = newProfileKey()

    val group = Group.Builder()
      .members(
        listOf(
          Member.Builder()
            .role(Member.Role.ADMINISTRATOR)
            .userId(groupOperations.encryptServiceId(admin1))
            .joinedAtRevision(4)
            .profileKey(encryptProfileKey(admin1, adminProfileKey))
            .build(),
          Member.Builder()
            .role(Member.Role.DEFAULT)
            .userId(groupOperations.encryptServiceId(member1))
            .joinedAtRevision(7)
            .profileKey(encryptProfileKey(member1, memberProfileKey))
            .build()
        )
      )
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.members).isEqualTo(
      DecryptedGroup.Builder()
        .members(
          listOf(
            DecryptedMember.Builder()
              .joinedAtRevision(4)
              .aciBytes(admin1.toByteString())
              .role(Member.Role.ADMINISTRATOR)
              .profileKey(ByteString.of(*adminProfileKey.serialize()))
              .build(),
            DecryptedMember.Builder()
              .joinedAtRevision(7)
              .role(Member.Role.DEFAULT)
              .aciBytes(member1.toByteString())
              .profileKey(ByteString.of(*memberProfileKey.serialize()))
              .build()
          )
        )
        .build().members
    )
  }

  @Test
  fun decrypt_pending_members_field_8() {
    val admin1 = from(UUID.randomUUID())
    val member1 = from(UUID.randomUUID())
    val member2 = from(UUID.randomUUID())
    val inviter1 = from(UUID.randomUUID())
    val inviter2 = from(UUID.randomUUID())

    val group = Group.Builder()
      .pendingMembers(
        listOf(
          PendingMember.Builder()
            .addedByUserId(groupOperations.encryptServiceId(inviter1))
            .timestamp(100)
            .member(
              Member.Builder()
                .role(Member.Role.ADMINISTRATOR)
                .userId(groupOperations.encryptServiceId(admin1))
                .build()
            )
            .build(),
          PendingMember.Builder()
            .addedByUserId(groupOperations.encryptServiceId(inviter1))
            .timestamp(200)
            .member(
              Member.Builder()
                .role(Member.Role.DEFAULT)
                .userId(groupOperations.encryptServiceId(member1))
                .build()
            )
            .build(),
          PendingMember.Builder()
            .addedByUserId(groupOperations.encryptServiceId(inviter2))
            .timestamp(1500)
            .member(
              Member.Builder()
                .userId(groupOperations.encryptServiceId(member2)).build()
            )
            .build()
        )
      )
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.pendingMembers).isEqualTo(
      DecryptedGroup.Builder()
        .pendingMembers(
          listOf(
            DecryptedPendingMember.Builder()
              .serviceIdBytes(admin1.toByteString())
              .serviceIdCipherText(groupOperations.encryptServiceId(admin1))
              .timestamp(100)
              .addedByAci(inviter1.toByteString())
              .role(Member.Role.ADMINISTRATOR)
              .build(),
            DecryptedPendingMember.Builder()
              .serviceIdBytes(member1.toByteString())
              .serviceIdCipherText(groupOperations.encryptServiceId(member1))
              .timestamp(200)
              .addedByAci(inviter1.toByteString())
              .role(Member.Role.DEFAULT)
              .build(),
            DecryptedPendingMember.Builder()
              .serviceIdBytes(member2.toByteString())
              .serviceIdCipherText(groupOperations.encryptServiceId(member2))
              .timestamp(1500)
              .addedByAci(inviter2.toByteString())
              .role(Member.Role.DEFAULT)
              .build()
          )
        )
        .build()
        .pendingMembers
    )
  }

  @Test
  fun decrypt_requesting_members_field_9() {
    val admin1 = from(UUID.randomUUID())
    val member1 = from(UUID.randomUUID())
    val adminProfileKey = newProfileKey()
    val memberProfileKey = newProfileKey()

    val group = Group.Builder()
      .requestingMembers(
        listOf(
          RequestingMember.Builder()
            .userId(groupOperations.encryptServiceId(admin1))
            .profileKey(encryptProfileKey(admin1, adminProfileKey))
            .timestamp(5000)
            .build(),
          RequestingMember.Builder()
            .userId(groupOperations.encryptServiceId(member1))
            .profileKey(encryptProfileKey(member1, memberProfileKey))
            .timestamp(15000)
            .build()
        )
      )
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.requestingMembers).isEqualTo(
      DecryptedGroup.Builder()
        .requestingMembers(
          listOf(
            DecryptedRequestingMember.Builder()
              .aciBytes(admin1.toByteString())
              .profileKey(ByteString.of(*adminProfileKey.serialize()))
              .timestamp(5000)
              .build(),
            DecryptedRequestingMember.Builder()
              .aciBytes(member1.toByteString())
              .profileKey(ByteString.of(*memberProfileKey.serialize()))
              .timestamp(15000)
              .build()
          )
        )
        .build()
        .requestingMembers
    )
  }

  @Test
  fun pass_through_group_link_password_field_10() {
    val password = ByteString.of(*Util.getSecretBytes(16))
    val group = Group.Builder()
      .inviteLinkPassword(password)
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.inviteLinkPassword).isEqualTo(password)
  }

  @Test
  fun decrypt_description_field_11() {
    val group = Group.Builder()
      .description(groupOperations.encryptDescription("Description!"))
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.description).isEqualTo("Description!")
  }

  @Test
  fun decrypt_announcements_field_12() {
    val group = Group.Builder()
      .announcementsOnly(true)
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.isAnnouncementGroup).isEqualTo(EnabledState.ENABLED)
  }

  @Test
  fun decrypt_banned_members_field_13() {
    val member1 = from(UUID.randomUUID())

    val group = Group.Builder()
      .bannedMembers(listOf(BannedMember.Builder().userId(groupOperations.encryptServiceId(member1)).build()))
      .build()

    val decryptedGroup = groupOperations.decryptGroup(group)

    assertThat(decryptedGroup.bannedMembers.size.toLong()).isEqualTo(1)
    assertThat(decryptedGroup.bannedMembers[0]).isEqualTo(DecryptedBannedMember.Builder().serviceIdBytes(member1.toByteString()).build())
  }

  private fun encryptProfileKey(aci: ACI, profileKey: ProfileKey): ByteString {
    return ByteString.of(*ClientZkGroupCipher(groupSecretParams).encryptProfileKey(profileKey, aci.libSignalAci).serialize())
  }

  companion object {
    private fun newProfileKey(): ProfileKey {
      try {
        return ProfileKey(Util.getSecretBytes(32))
      } catch (e: InvalidInputException) {
        throw AssertionError(e)
      }
    }
  }
}
