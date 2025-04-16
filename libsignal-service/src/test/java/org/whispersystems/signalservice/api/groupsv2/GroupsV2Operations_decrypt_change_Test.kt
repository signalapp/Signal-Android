package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import okio.ByteString
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.groups.ClientZkGroupCipher
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.libsignal.zkgroup.groups.UuidCiphertext
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.GroupChange
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeleteMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeletePendingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.PromotePendingPniAciMemberProfileKeyAction
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.signal.storageservice.protos.groups.local.DecryptedString
import org.signal.storageservice.protos.groups.local.DecryptedTimer
import org.signal.storageservice.protos.groups.local.EnabledState
import org.whispersystems.signalservice.api.groupsv2.DecryptChangeVerificationMode.Companion.alreadyTrusted
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations.GroupOperations
import org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.util.Util
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

class GroupsV2Operations_decrypt_change_Test {
  private lateinit var groupSecretParams: GroupSecretParams
  private lateinit var groupOperations: GroupOperations
  private lateinit var clientZkOperations: ClientZkOperations
  private lateinit var server: TestZkGroupServer

  @Before
  fun setup() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    server = TestZkGroupServer()
    groupSecretParams = GroupSecretParams.deriveFromMasterKey(GroupMasterKey(Util.getSecretBytes(32)))
    clientZkOperations = ClientZkOperations(server.serverPublicParams)
    groupOperations = GroupsV2Operations(clientZkOperations, 1000).forGroup(groupSecretParams)
  }

  @Test
  fun ensure_GroupV2Operations_decryptChange_knows_about_all_fields_of_DecryptedGroupChange() {
    assertThat(
      actual = getMaxDeclaredFieldNumber(DecryptedGroupChange::class.java),
      name = "GroupV2Operations#decryptChange and its tests need updating to account for new fields on " + DecryptedGroupChange::class.java.name
    ).isEqualTo(24)
  }

  @Test
  @Throws(IOException::class, VerificationFailedException::class, InvalidGroupStateException::class)
  fun cannot_decrypt_change_with_epoch_higher_than_known() {
    val change = GroupChange.Builder()
      .changeEpoch(GroupsV2Operations.HIGHEST_KNOWN_EPOCH + 1)
      .build()

    val decryptedGroupChangeOptional = groupOperations.decryptChange(change, alreadyTrusted)

    assertThat(decryptedGroupChangeOptional.isPresent).isFalse()
  }

  @Test
  fun can_pass_revision_through_encrypt_and_decrypt_methods() {
    assertDecryption(
      GroupChange.Actions.Builder()
        .revision(1),
      DecryptedGroupChange.Builder()
        .revision(1)
    )
  }

  @Test
  fun can_decrypt_member_additions_field3() {
    val self = ACI.from(UUID.randomUUID())
    val newMember = ACI.from(UUID.randomUUID())
    val profileKey = newProfileKey()
    val groupCandidate = groupCandidate(newMember, profileKey)

    assertDecryption(
      groupOperations.createModifyGroupMembershipChange(setOf(groupCandidate), emptySet<ServiceId>(), self)
        .revision(10),
      DecryptedGroupChange.Builder()
        .revision(10)
        .newMembers(
          listOf(
            DecryptedMember.Builder()
              .role(Member.Role.DEFAULT)
              .profileKey(ByteString.of(*profileKey.serialize()))
              .joinedAtRevision(10)
              .aciBytes(newMember.toByteString())
              .build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_member_direct_join_field3() {
    val newMember = ACI.from(UUID.randomUUID())
    val profileKey = newProfileKey()
    val groupCandidate = groupCandidate(newMember, profileKey)

    assertDecryption(
      groupOperations.createGroupJoinDirect(groupCandidate.expiringProfileKeyCredential.get())
        .revision(10),
      DecryptedGroupChange.Builder()
        .revision(10)
        .newMembers(
          listOf(
            DecryptedMember.Builder()
              .role(Member.Role.DEFAULT)
              .profileKey(ByteString.of(*profileKey.serialize()))
              .joinedAtRevision(10)
              .aciBytes(newMember.toByteString())
              .build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_member_additions_direct_to_admin_field3() {
    val self = ACI.from(UUID.randomUUID())
    val newMember = ACI.from(UUID.randomUUID())
    val profileKey = newProfileKey()
    val groupCandidate = groupCandidate(newMember, profileKey)

    assertDecryption(
      groupOperations.createModifyGroupMembershipChange(setOf(groupCandidate), emptySet<ServiceId>(), self)
        .revision(10),
      DecryptedGroupChange.Builder()
        .revision(10)
        .newMembers(
          listOf(
            DecryptedMember.Builder()
              .role(Member.Role.DEFAULT)
              .profileKey(ByteString.of(*profileKey.serialize()))
              .joinedAtRevision(10)
              .aciBytes(newMember.toByteString())
              .build()
          )
        )
    )
  }

  @Test(expected = InvalidGroupStateException::class)
  @Throws(IOException::class, VerificationFailedException::class, InvalidGroupStateException::class)
  fun cannot_decrypt_member_additions_with_bad_cipher_text_field3() {
    val randomPresentation = Util.getSecretBytes(5)
    val actions = GroupChange.Actions.Builder()

    actions.addMembers(
      listOf(
        AddMemberAction.Builder().added(
          Member.Builder().role(Member.Role.DEFAULT)
            .presentation(ByteString.of(*randomPresentation)).build()
        ).build()
      )
    )

    groupOperations.decryptChange(GroupChange.Builder().actions(actions.build().encodeByteString()).build(), alreadyTrusted)
  }

  @Test
  fun can_decrypt_member_removals_field4() {
    val oldMember = ACI.from(UUID.randomUUID())

    assertDecryption(
      groupOperations.createRemoveMembersChange(setOf(oldMember), false, emptyList())
        .revision(10),
      DecryptedGroupChange.Builder()
        .revision(10)
        .deleteMembers(listOf(oldMember.toByteString()))
    )
  }

  @Test(expected = InvalidGroupStateException::class)
  fun cannot_decrypt_member_removals_with_bad_cipher_text_field4() {
    val randomPresentation = Util.getSecretBytes(5)
    val actions = GroupChange.Actions.Builder()

    actions.deleteMembers(listOf(DeleteMemberAction.Builder().deletedUserId(ByteString.of(*randomPresentation)).build()))

    groupOperations.decryptChange(GroupChange.Builder().actions(actions.build().encodeByteString()).build(), alreadyTrusted)
  }

  @Test
  fun can_decrypt_modify_member_action_role_to_admin_field5() {
    val member = ACI.from(UUID.randomUUID())

    assertDecryption(
      groupOperations.createChangeMemberRole(member, Member.Role.ADMINISTRATOR),
      DecryptedGroupChange.Builder()
        .modifyMemberRoles(
          listOf(
            DecryptedModifyMemberRole.Builder()
              .aciBytes(member.toByteString())
              .role(Member.Role.ADMINISTRATOR)
              .build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_modify_member_action_role_to_member_field5() {
    val member = ACI.from(UUID.randomUUID())

    assertDecryption(
      groupOperations.createChangeMemberRole(member, Member.Role.DEFAULT),
      DecryptedGroupChange.Builder()
        .modifyMemberRoles(
          listOf(
            DecryptedModifyMemberRole.Builder()
              .aciBytes(member.toByteString())
              .role(Member.Role.DEFAULT).build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_modify_member_profile_key_action_field6() {
    val self = ACI.from(UUID.randomUUID())
    val profileKey = newProfileKey()
    val groupCandidate = groupCandidate(self, profileKey)

    assertDecryption(
      groupOperations.createUpdateProfileKeyCredentialChange(groupCandidate.expiringProfileKeyCredential.get())
        .revision(10),
      DecryptedGroupChange.Builder()
        .revision(10)
        .modifiedProfileKeys(
          listOf(
            DecryptedMember.Builder()
              .role(Member.Role.UNKNOWN)
              .joinedAtRevision(-1)
              .profileKey(ByteString.of(*profileKey.serialize()))
              .aciBytes(self.toByteString())
              .build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_member_invitations_field7() {
    val self = ACI.from(UUID.randomUUID())
    val newMember = ACI.from(UUID.randomUUID())
    val groupCandidate = GroupCandidate(newMember, Optional.empty())

    assertDecryption(
      groupOperations.createModifyGroupMembershipChange(setOf(groupCandidate), emptySet(), self)
        .revision(13),
      DecryptedGroupChange.Builder()
        .revision(13)
        .newPendingMembers(
          listOf(
            DecryptedPendingMember.Builder()
              .addedByAci(self.toByteString())
              .serviceIdCipherText(groupOperations.encryptServiceId(newMember))
              .role(Member.Role.DEFAULT)
              .serviceIdBytes(newMember.toByteString())
              .build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_pending_member_removals_field8() {
    val oldMember = ACI.from(UUID.randomUUID())
    val uuidCiphertext = UuidCiphertext(groupOperations.encryptServiceId(oldMember).toByteArray())

    assertDecryption(
      groupOperations.createRemoveInvitationChange(setOf(uuidCiphertext)),
      DecryptedGroupChange.Builder()
        .deletePendingMembers(
          listOf(
            DecryptedPendingMemberRemoval.Builder()
              .serviceIdBytes(oldMember.toByteString())
              .serviceIdCipherText(ByteString.of(*uuidCiphertext.serialize()))
              .build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_pending_member_removals_with_bad_cipher_text_field8() {
    val uuidCiphertext = Util.getSecretBytes(60)

    assertDecryption(
      GroupChange.Actions.Builder()
        .deletePendingMembers(
          listOf(
            DeletePendingMemberAction.Builder()
              .deletedUserId(ByteString.of(*uuidCiphertext)).build()
          )
        ),
      DecryptedGroupChange.Builder()
        .deletePendingMembers(
          listOf(
            DecryptedPendingMemberRemoval.Builder()
              .serviceIdBytes(UuidUtil.toByteString(UuidUtil.UNKNOWN_UUID))
              .serviceIdCipherText(ByteString.of(*uuidCiphertext))
              .build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_promote_pending_member_field9() {
    val newMember = ACI.from(UUID.randomUUID())
    val profileKey = newProfileKey()
    val groupCandidate = groupCandidate(newMember, profileKey)

    assertDecryption(
      groupOperations.createAcceptInviteChange(groupCandidate.expiringProfileKeyCredential.get()),
      DecryptedGroupChange.Builder()
        .promotePendingMembers(
          listOf(
            DecryptedMember.Builder()
              .aciBytes(newMember.toByteString())
              .role(Member.Role.DEFAULT)
              .profileKey(ByteString.of(*profileKey.serialize()))
              .joinedAtRevision(-1)
              .build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_title_field_10() {
    assertDecryption(
      groupOperations.createModifyGroupTitle("New title"),
      DecryptedGroupChange.Builder()
        .newTitle(DecryptedString.Builder().value_("New title").build())
    )
  }

  @Test
  fun can_decrypt_avatar_key_field_11() {
    assertDecryption(
      GroupChange.Actions.Builder()
        .modifyAvatar(GroupChange.Actions.ModifyAvatarAction.Builder().avatar("New avatar").build()),
      DecryptedGroupChange.Builder()
        .newAvatar(DecryptedString.Builder().value_("New avatar").build())
    )
  }

  @Test
  fun can_decrypt_timer_value_field_12() {
    assertDecryption(
      groupOperations.createModifyGroupTimerChange(100),
      DecryptedGroupChange.Builder()
        .newTimer(DecryptedTimer.Builder().duration(100).build())
    )
  }

  @Test
  fun can_pass_through_new_attribute_access_rights_field_13() {
    assertDecryption(
      groupOperations.createChangeAttributesRights(AccessControl.AccessRequired.MEMBER),
      DecryptedGroupChange.Builder()
        .newAttributeAccess(AccessControl.AccessRequired.MEMBER)
    )
  }

  @Test
  fun can_pass_through_new_membership_rights_field_14() {
    assertDecryption(
      groupOperations.createChangeMembershipRights(AccessControl.AccessRequired.ADMINISTRATOR),
      DecryptedGroupChange.Builder()
        .newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
    )
  }

  @Test
  fun can_pass_through_new_add_by_invite_link_rights_field_15() {
    assertDecryption(
      groupOperations.createChangeJoinByLinkRights(AccessControl.AccessRequired.ADMINISTRATOR),
      DecryptedGroupChange.Builder()
        .newInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
    )
  }

  @Test
  fun can_pass_through_new_add_by_invite_link_rights_field_15_unsatisfiable() {
    assertDecryption(
      groupOperations.createChangeJoinByLinkRights(AccessControl.AccessRequired.UNSATISFIABLE),
      DecryptedGroupChange.Builder()
        .newInviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE)
    )
  }

  @Test
  fun can_decrypt_member_requests_field16() {
    val newRequestingMember = ACI.from(UUID.randomUUID())
    val profileKey = newProfileKey()
    val groupCandidate = groupCandidate(newRequestingMember, profileKey)

    assertDecryption(
      groupOperations.createGroupJoinRequest(groupCandidate.expiringProfileKeyCredential.get())
        .revision(10),
      DecryptedGroupChange.Builder()
        .revision(10)
        .newRequestingMembers(
          listOf(
            DecryptedRequestingMember.Builder()
              .aciBytes(newRequestingMember.toByteString())
              .profileKey(ByteString.of(*profileKey.serialize()))
              .build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_member_requests_refusals_field17() {
    val newRequestingMember = ACI.from(UUID.randomUUID())

    assertDecryption(
      groupOperations.createRefuseGroupJoinRequest(setOf(newRequestingMember), true, emptyList())
        .revision(10),
      DecryptedGroupChange.Builder()
        .revision(10)
        .deleteRequestingMembers(listOf(newRequestingMember.toByteString()))
        .newBannedMembers(listOf(DecryptedBannedMember.Builder().serviceIdBytes(newRequestingMember.toByteString()).build()))
    )
  }

  @Test
  fun can_decrypt_promote_requesting_members_field18() {
    val newRequestingMember = UUID.randomUUID()

    assertDecryption(
      groupOperations.createApproveGroupJoinRequest(setOf(newRequestingMember))
        .revision(15),
      DecryptedGroupChange.Builder()
        .revision(15)
        .promoteRequestingMembers(
          listOf(
            DecryptedApproveMember.Builder()
              .role(Member.Role.DEFAULT)
              .aciBytes(UuidUtil.toByteString(newRequestingMember))
              .build()
          )
        )
    )
  }

  @Test
  fun can_pass_through_new_invite_link_password_field19() {
    val newPassword = Util.getSecretBytes(16)

    assertDecryption(
      GroupChange.Actions.Builder()
        .modifyInviteLinkPassword(
          GroupChange.Actions.ModifyInviteLinkPasswordAction.Builder()
            .inviteLinkPassword(ByteString.of(*newPassword))
            .build()
        ),
      DecryptedGroupChange.Builder()
        .newInviteLinkPassword(ByteString.of(*newPassword))
    )
  }

  @Test
  fun can_pass_through_new_description_field20() {
    assertDecryption(
      groupOperations.createModifyGroupDescription("New Description"),
      DecryptedGroupChange.Builder()
        .newDescription(DecryptedString.Builder().value_("New Description").build())
    )
  }

  @Test
  fun can_pass_through_new_announcement_only_field21() {
    assertDecryption(
      GroupChange.Actions.Builder()
        .modifyAnnouncementsOnly(
          GroupChange.Actions.ModifyAnnouncementsOnlyAction.Builder()
            .announcementsOnly(true)
            .build()
        ),
      DecryptedGroupChange.Builder()
        .newIsAnnouncementGroup(EnabledState.ENABLED)
    )
  }

  @Test
  fun can_decrypt_member_bans_field22() {
    val ban = ACI.from(UUID.randomUUID())

    assertDecryption(
      groupOperations.createBanServiceIdsChange(setOf(ban), false, emptyList())
        .revision(13),
      DecryptedGroupChange.Builder()
        .revision(13)
        .newBannedMembers(
          listOf(
            DecryptedBannedMember.Builder()
              .serviceIdBytes(ban.toByteString())
              .build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_banned_member_removals_field23() {
    val ban = ACI.from(UUID.randomUUID())

    assertDecryption(
      groupOperations.createUnbanServiceIdsChange(setOf<ServiceId>(ban))
        .revision(13),
      DecryptedGroupChange.Builder()
        .revision(13)
        .deleteBannedMembers(
          listOf(
            DecryptedBannedMember.Builder()
              .serviceIdBytes(ban.toByteString()).build()
          )
        )
    )
  }

  @Test
  fun can_decrypt_promote_pending_pni_aci_member_field24() {
    val memberAci = ACI.from(UUID.randomUUID())
    val memberPni = PNI.from(UUID.randomUUID())
    val profileKey = newProfileKey()

    val builder = GroupChange.Actions.Builder()
      .sourceServiceId(groupOperations.encryptServiceId(memberPni))
      .revision(5)
      .promotePendingPniAciMembers(
        listOf(
          PromotePendingPniAciMemberProfileKeyAction.Builder()
            .userId(groupOperations.encryptServiceId(memberAci))
            .pni(groupOperations.encryptServiceId(memberPni))
            .profileKey(encryptProfileKey(memberAci, profileKey))
            .build()
        )
      )

    assertDecryptionWithEditorSet(
      builder,
      DecryptedGroupChange.Builder()
        .editorServiceIdBytes(memberAci.toByteString())
        .revision(5)
        .promotePendingPniAciMembers(
          listOf(
            DecryptedMember.Builder()
              .aciBytes(memberAci.toByteString())
              .pniBytes(memberPni.toByteString())
              .role(Member.Role.DEFAULT)
              .profileKey(ByteString.of(*profileKey.serialize()))
              .joinedAtRevision(5)
              .build()
          )
        )
    )
  }

  private fun encryptProfileKey(aci: ACI, profileKey: ProfileKey): ByteString {
    return ByteString.of(*ClientZkGroupCipher(groupSecretParams).encryptProfileKey(profileKey, aci.libSignalAci).serialize())
  }

  private fun groupCandidate(aci: ACI, profileKey: ProfileKey): GroupCandidate {
    try {
      val profileOperations = clientZkOperations.profileOperations
      val commitment = profileKey.getCommitment(aci.libSignalAci)
      val requestContext = profileOperations.createProfileKeyCredentialRequestContext(aci.libSignalAci, profileKey)
      val request = requestContext.request
      val expiringProfileKeyCredentialResponse = server.getExpiringProfileKeyCredentialResponse(request, aci, commitment, Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS))
      val profileKeyCredential = profileOperations.receiveExpiringProfileKeyCredential(requestContext, expiringProfileKeyCredentialResponse)
      val groupCandidate = GroupCandidate(aci, Optional.of(profileKeyCredential))

      val presentation = profileOperations.createProfileKeyCredentialPresentation(groupSecretParams, profileKeyCredential)
      server.assertProfileKeyCredentialPresentation(groupSecretParams.publicParams, presentation, Instant.now())

      return groupCandidate
    } catch (e: VerificationFailedException) {
      throw AssertionError(e)
    }
  }

  private fun assertDecryption(
    inputChange: GroupChange.Actions.Builder,
    expectedDecrypted: DecryptedGroupChange.Builder
  ) {
    val editor = ACI.from(UUID.randomUUID())
    assertDecryptionWithEditorSet(inputChange.sourceServiceId(groupOperations.encryptServiceId(editor)), expectedDecrypted.editorServiceIdBytes(editor.toByteString()))
  }

  private fun assertDecryptionWithEditorSet(
    inputChange: GroupChange.Actions.Builder,
    expectedDecrypted: DecryptedGroupChange.Builder
  ) {
    val actions = inputChange.build()

    val change = GroupChange.Builder()
      .actions(actions.encodeByteString())
      .build()

    val decryptedGroupChange = decrypt(change)

    assertThat(decryptedGroupChange).isEqualTo(expectedDecrypted.build())
  }

  private fun decrypt(build: GroupChange): DecryptedGroupChange {
    try {
      return groupOperations.decryptChange(build, alreadyTrusted).get()
    } catch (e: IOException) {
      throw AssertionError(e)
    } catch (e: VerificationFailedException) {
      throw AssertionError(e)
    } catch (e: InvalidGroupStateException) {
      throw AssertionError(e)
    }
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
